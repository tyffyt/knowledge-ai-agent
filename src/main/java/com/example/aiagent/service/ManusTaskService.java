package com.example.aiagent.service;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.example.aiagent.agent.ManusTaskAgent;
import com.example.aiagent.agent.ManusTaskContext;
import com.example.aiagent.agent.SubAgentRunner;
import com.example.aiagent.agent.model.AgentState;
import com.example.aiagent.advisor.MyLoggerAdvisor;
import com.example.aiagent.model.ManusTask;
import com.example.aiagent.model.ManusTaskListItem;
import com.example.aiagent.model.ManusTaskStatus;
import com.example.aiagent.tool.KnowledgeSearchTool;
import com.example.aiagent.tool.PlanManagementTool;
import com.example.aiagent.tool.RegisterDeliverableTool;
import com.example.aiagent.tool.SubAgentTool;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbacks;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Manus 超级智能体任务服务
 * 负责任务的创建、异步执行、事件落库与 SSE 推送、停止与查询
 * 数据存储于 manus_task 集合（chat_memory_db 库，manus_ 前缀与 chat_memory 集合区分）
 */
@Slf4j
@Service
public class ManusTaskService {

    /**
     * 任务列表最大返回条数（无分页，直接截断，避免全量传输）
     */
    private static final int LIST_LIMIT = 50;

    /**
     * 任务标题长度（创建时截取任务描述前缀）
     */
    private static final int TITLE_LENGTH = 20;

    /**
     * 任务描述长度上限（输入校验）
     */
    private static final int TASK_MAX_LENGTH = 20000;

    private static final String SYSTEM_PROMPT = """
            你是 YuManus，一个能自主规划并执行多步任务的通用智能体。你可以使用多种工具高效完成用户的复杂请求。
            工作纪律：
            1. 收到任务后，先用 planCreate 工具把任务拆解为 3~8 个简洁、可执行的步骤，形成计划；
            2. 开始执行某一步前，用 planUpdate 把该步骤标记为 in_progress；完成后标记为 done；
            3. 严格按计划逐步执行；如果执行中发现计划不符合实际，可以再次调用 planCreate 调整计划；
            4. 所有步骤完成后，输出面向用户的最终报告，然后调用 doTerminate 工具结束任务。最终报告必须满足：
               - 第一句直接陈述答案或结论，禁止以"根据搜索结果""我已经""现在我需要""让我"等复述执行过程的句子开头；
               - 报告通篇只包含给用户看的内容（答案、结论、建议、数据），禁止出现任何描述自己工作过程或下一步打算的语句；
               - 使用 Markdown 结构组织（结论先行、要点分条、信息完整），用户不需要查看执行过程就能直接使用报告；
            5. 调用 doTerminate 结束任务前，确保所有计划步骤都已标记为 done（未执行的标记 skipped）；
            6. 如实汇报工具的执行结果，禁止编造结果；
            7. 凡是生成了文件（PDF、下载的资源、写出的文档），必须立即用 register 工具登记为交付物，并给出简短的内容说明；报告与回复中不要写"如何获取文件"的操作指引（例如"请通过文档管理功能下载"），登记后的交付物会自动以预览/下载卡片展示在报告下方，你只需说明产出了什么文件；
            8. 涉及用户本地笔记与知识库的问题优先用 knowledgeSearch 检索，实时信息（新闻、行情等）用网络搜索；
            9. 派发纪律：需要多步深度调研、多角度检索或成文档撰写的独立子任务，可用 delegate 派发给对应角色的子智能体执行，你负责拆解任务、传递自包含的子任务描述并汇总结果；简单步骤自己直接做，不要滥用派发，同一子任务不要重复派发。
            """;

    private static final String NEXT_STEP_PROMPT = """
            根据任务当前进展决定下一步行动：继续执行计划中的步骤，或更新计划状态。
            记住：开始一步前把该步骤标记为 in_progress，完成后标记为 done。
            如果所有步骤都已完成，请输出面向用户的最终总结，并调用 doTerminate 工具结束任务。
            """;

    private final MongoTemplate mongoTemplate;

    // 共享基础工具（与问答/旧 Manus 同一套 Bean）
    private final ToolCallback[] allTools;

    // 固定使用 DashScope 对话模型（市面 ReAct 产品惯例：模型配置化、不做运行时切换）
    private final ChatModel dashscopeChatModel;

    // 知识库向量存储（仅 Manus 任务使用，不进共享工具池）
    private final VectorStore knowledgeVectorStore;

    // 知识库检索工具（无状态，全任务共享一个实例）
    private final KnowledgeSearchTool knowledgeSearchTool;

    // 子智能体对话模型（DeepSeek，与主智能体的千问分工）
    private final ChatModel deepseekChatModel;

    // 步骤上限，可通过 manus.agent.max-steps 配置
    @Value("${manus.agent.max-steps:20}")
    private int maxSteps;

    // 子智能体步骤上限，可通过 manus.agent.sub-max-steps 配置（默认 10）
    @Value("${manus.agent.sub-max-steps:10}")
    private int subMaxSteps;

    // 任务执行线程池大小，可通过 manus.agent.pool-size 配置（默认 4）
    @Value("${manus.agent.pool-size:4}")
    private int poolSize;

    // 任务执行线程池（@PostConstruct 按配置创建）
    private ExecutorService executor;

    // 子智能体执行器（@PostConstruct 创建，依赖配置的步骤上限）
    private SubAgentRunner subAgentRunner;

    // 线程序号（线程命名用）
    private final AtomicInteger threadSeq = new AtomicInteger();

    // 每任务一把锁：同一任务的文档读改写与事件广播串行化，避免并发丢更新与事件重复
    private final Map<String, Object> taskLocks = new ConcurrentHashMap<>();

    // taskId -> 订阅中的 SSE 连接
    private final Map<String, List<SseEmitter>> emitterMap = new ConcurrentHashMap<>();

    // taskId -> 运行中任务上下文（用于停止请求）
    private final Map<String, ManusTaskContext> runningContexts = new ConcurrentHashMap<>();

    public ManusTaskService(MongoTemplate mongoTemplate, ToolCallback[] allTools, ChatModel dashscopeChatModel,
                            @Qualifier("knowledgeVectorStore") VectorStore knowledgeVectorStore,
                            @Qualifier("openAiChatModel") ChatModel deepseekChatModel) {
        this.mongoTemplate = mongoTemplate;
        this.allTools = allTools;
        this.dashscopeChatModel = dashscopeChatModel;
        this.knowledgeVectorStore = knowledgeVectorStore;
        this.knowledgeSearchTool = new KnowledgeSearchTool(knowledgeVectorStore);
        this.deepseekChatModel = deepseekChatModel;
    }

    /**
     * 按配置初始化任务执行线程池与子智能体执行器
     */
    @PostConstruct
    public void initExecutor() {
        executor = Executors.newFixedThreadPool(Math.max(1, poolSize), this::newTaskThread);
        subAgentRunner = new SubAgentRunner(allTools, buildSubChatClient(), subMaxSteps);
    }

    /**
     * 构建子智能体使用的 ChatClient（DeepSeek 模型 + 日志 Advisor，Agent 自行管理消息）
     *
     * @return ChatClient
     */
    private ChatClient buildSubChatClient() {
        return ChatClient.builder(deepseekChatModel)
                .defaultAdvisors(new MyLoggerAdvisor())
                .build();
    }

    /**
     * 创建任务并异步执行
     *
     * @param task 任务描述
     * @return 已落库的任务文档（含 ID 与初始状态）
     */
    public ManusTask createTask(String task) {
        if (StrUtil.isBlank(task)) {
            throw new IllegalArgumentException("任务描述不能为空");
        }
        if (task.length() > TASK_MAX_LENGTH) {
            throw new IllegalArgumentException("任务描述过长，请控制在 " + TASK_MAX_LENGTH + " 字以内");
        }
        ManusTask taskDoc = new ManusTask();
        taskDoc.setTitle(task.length() > TITLE_LENGTH ? task.substring(0, TITLE_LENGTH) : task);
        taskDoc.setTask(task);
        taskDoc.setStatus(ManusTaskStatus.PENDING);
        taskDoc.setPlan(new ArrayList<>());
        taskDoc.setEvents(new ArrayList<>());
        taskDoc.setCreatedAt(Instant.now());
        taskDoc.setUpdatedAt(Instant.now());
        mongoTemplate.save(taskDoc);
        log.info("Manus 任务已创建: id={}", taskDoc.getId());
        executor.submit(() -> executeTask(taskDoc.getId()));
        return taskDoc;
    }

    /**
     * 订阅任务的结构化事件流（SSE）
     * 从 afterIndex 之后的事件开始推送（增量，配合前端追问/续接场景避免整段重复回放）；
     * 任务已结束时推送完毕立即关闭；执行中则保持长连接接收后续事件
     *
     * @param taskId     任务 ID
     * @param afterIndex 起始事件序号（之前的已收到，0 表示从头回放）
     * @return SSE 连接
     */
    public SseEmitter subscribe(String taskId, int afterIndex) {
        SseEmitter emitter = new SseEmitter(0L);
        Object lock = taskLocks.computeIfAbsent(taskId, k -> new Object());
        synchronized (lock) {
            // 锁内读取文档：回放与终态判断基于同一快照，避免回放与注册之间丢事件或终态误判
            ManusTask taskDoc = mongoTemplate.findById(taskId, ManusTask.class);
            if (taskDoc == null) {
                throw new IllegalArgumentException("任务不存在：" + taskId);
            }
            try {
                if (taskDoc.getEvents() != null) {
                    List<ManusTask.EventEntry> events = taskDoc.getEvents();
                    for (int i = Math.max(0, afterIndex); i < events.size(); i++) {
                        emitter.send(JSONUtil.toJsonStr(events.get(i)));
                    }
                }
            } catch (IOException e) {
                log.warn("回放任务事件失败: taskId={}", taskId, e);
                emitter.completeWithError(e);
                return emitter;
            }
            if (isFinished(taskDoc.getStatus())) {
                emitter.complete();
            } else {
                emitterMap.computeIfAbsent(taskId, k -> new CopyOnWriteArrayList<>()).add(emitter);
            }
        }
        emitter.onCompletion(() -> removeEmitter(taskId, emitter));
        emitter.onTimeout(emitter::complete);
        return emitter;
    }

    /**
     * 查询任务详情（含计划、事件日志与最终报告，用于回放）
     *
     * @param taskId 任务 ID
     * @return 任务文档，不存在返回 null
     */
    public ManusTask getTask(String taskId) {
        if (StrUtil.isBlank(taskId)) {
            return null;
        }
        return mongoTemplate.findById(taskId, ManusTask.class);
    }

    /**
     * 查询任务列表（按更新时间倒序，最多 50 条；不含事件日志，仅含计划进度）
     *
     * @return 任务列表项
     */
    public List<ManusTaskListItem> listTasks() {
        Query query = new Query();
        query.fields().include("title", "task", "status", "plan", "createdAt", "updatedAt");
        List<ManusTask> tasks = mongoTemplate.find(query, ManusTask.class);
        tasks.sort(Comparator.comparing(
                (ManusTask t) -> t.getUpdatedAt() != null ? t.getUpdatedAt() : t.getCreatedAt(),
                Comparator.reverseOrder()));
        return tasks.stream().limit(LIST_LIMIT).map(this::toListItem).toList();
    }

    /**
     * 停止任务
     * 执行中的任务在当前步骤执行完毕后停止；排队中未开始的任务直接标记停止
     *
     * @param taskId 任务 ID
     * @return 结果说明
     */
    public String stopTask(String taskId) {
        Object lock = taskLocks.computeIfAbsent(taskId, k -> new Object());
        synchronized (lock) {
            ManusTask latest = mongoTemplate.findById(taskId, ManusTask.class);
            if (latest == null) {
                throw new IllegalArgumentException("任务不存在：" + taskId);
            }
            if (isFinished(latest.getStatus())) {
                throw new IllegalArgumentException("任务已结束，无法停止");
            }
            ManusTaskContext context = runningContexts.get(taskId);
            if (context != null) {
                // 执行中（运行上下文在任务锁内注册，此处读到 RUNNING 必有上下文）
                context.requestStop();
                log.info("Manus 任务收到停止请求: taskId={}", taskId);
                return "停止请求已发送，任务将在当前步骤执行完毕后停止";
            }
            // 排队中尚未开始：立即补收尾（final 事件 + 状态 + 关闭订阅连接）
            finishTask(taskId, null, ManusTaskStatus.STOPPED, null, null);
            return "任务已停止（尚未开始执行）";
        }
    }

    /**
     * 对已结束的任务追加用户追问，任务回到 RUNNING 并重新执行一轮
     * 上下文采用摘要重建：原任务 + 历次追问 + 历轮报告 + 当前计划 + 最近事件 + 本次追问
     *
     * @param taskId  任务 ID
     * @param message 追问内容
     * @return 更新后的任务文档
     */
    public ManusTask addFollowUp(String taskId, String message) {
        if (StrUtil.isBlank(message)) {
            throw new IllegalArgumentException("追问内容不能为空");
        }
        if (message.length() > TASK_MAX_LENGTH) {
            throw new IllegalArgumentException("追问内容过长，请控制在 " + TASK_MAX_LENGTH + " 字以内");
        }
        Object lock = taskLocks.computeIfAbsent(taskId, k -> new Object());
        ManusTask updated;
        synchronized (lock) {
            ManusTask taskDoc = mongoTemplate.findById(taskId, ManusTask.class);
            if (taskDoc == null) {
                throw new IllegalArgumentException("任务不存在：" + taskId);
            }
            if (!isFinished(taskDoc.getStatus())) {
                throw new IllegalArgumentException("任务正在执行中，请等本轮结束后再追加消息");
            }
            if (taskDoc.getFollowUps() == null) {
                taskDoc.setFollowUps(new ArrayList<>());
            }
            taskDoc.getFollowUps().add(message.trim());
            if (taskDoc.getEvents() == null) {
                taskDoc.setEvents(new ArrayList<>());
            }
            taskDoc.getEvents().add(new ManusTask.EventEntry("user_message", Instant.now().toString(),
                    message.trim(), null, null, null, null, null, "main"));
            taskDoc.setStatus(ManusTaskStatus.RUNNING);
            taskDoc.setUpdatedAt(Instant.now());
            mongoTemplate.save(taskDoc);
            broadcast(taskId, JSONUtil.toJsonStr(taskDoc.getEvents().get(taskDoc.getEvents().size() - 1)));
            // 锁内取最新快照返回，避免读到被执行线程并发修改的过期数据
            updated = taskDoc;
        }
        log.info("Manus 任务收到追问: taskId={}", taskId);
        executor.submit(() -> executeTask(taskId));
        return updated;
    }

    /**
     * 执行任务（在线程池中运行）
     * 组装任务上下文、规划工具与任务智能体，循环执行直到完成/停止/出错
     *
     * @param taskId 任务 ID
     */
    private void executeTask(String taskId) {
        Object lock = taskLocks.computeIfAbsent(taskId, k -> new Object());
        ManusTaskContext context = new ManusTaskContext(taskId, entry -> publishEvent(taskId, entry),
                deliverable -> registerDeliverable(taskId, deliverable));
        ManusTaskAgent agent = null;
        String initialMessage = null;
        try {
            synchronized (lock) {
                ManusTask taskDoc = mongoTemplate.findById(taskId, ManusTask.class);
                if (taskDoc == null) {
                    return;
                }
                if (taskDoc.getStatus() == ManusTaskStatus.STOPPED) {
                    // 排队期间被停止：stopTask 已即时收尾（final 事件与状态），此处无需重复
                    return;
                }
                // 首轮以任务描述为初始消息；追问轮以摘要重建上下文
                initialMessage = (taskDoc.getFollowUps() == null || taskDoc.getFollowUps().isEmpty())
                        ? taskDoc.getTask()
                        : buildFollowUpContext(taskDoc);
                taskDoc.setStatus(ManusTaskStatus.RUNNING);
                taskDoc.setUpdatedAt(Instant.now());
                mongoTemplate.save(taskDoc);
                // 运行上下文在任务锁内注册：保证 stopTask 在锁内读到 RUNNING 时必能拿到上下文
                runningContexts.put(taskId, context);
            }

            agent = new ManusTaskAgent(mergeTools(context), context);
            agent.setName("manus-task");
            agent.setSystemPrompt(SYSTEM_PROMPT);
            agent.setNextStepPrompt(NEXT_STEP_PROMPT);
            agent.setMaxSteps(maxSteps);
            agent.setChatClient(buildChatClient());
            // 初始消息进入上下文（等价于 BaseAgent.run 的消息初始化）
            agent.getMessageList().add(new UserMessage(initialMessage));

            boolean stepLimitReached = false;
            for (int i = 0; i < maxSteps; i++) {
                if (context.isStopRequested()) {
                    break;
                }
                agent.setCurrentStep(i + 1);
                log.info("Manus 任务执行步骤 {}/{}: taskId={}", i + 1, maxSteps, taskId);
                agent.step();
                AgentState state = agent.getState();
                if (state == AgentState.FINISHED || state == AgentState.ERROR) {
                    break;
                }
                if (i == maxSteps - 1) {
                    stepLimitReached = true;
                }
            }

            ManusTaskStatus finalStatus;
            String finalReport = agent.getLastAssistantText();
            String errorMessage = null;
            if (context.isStopRequested()) {
                finalStatus = ManusTaskStatus.STOPPED;
            } else if (agent.getState() == AgentState.ERROR) {
                finalStatus = ManusTaskStatus.ERROR;
                errorMessage = agent.getLastErrorContent();
            } else {
                finalStatus = ManusTaskStatus.COMPLETED;
                if (stepLimitReached) {
                    finalReport = (StrUtil.isBlank(finalReport) ? "" : finalReport + "\n\n")
                            + "（已达最大步骤数 " + maxSteps + "，任务可能未完全完成）";
                }
                // 专职报告生成：模型的最后一条消息常是"现在调用 doTerminate"类过程独白，
                // 收尾时额外做一次无工具的 LLM 调用生成面向用户的报告，失败才回退思考文本
                if (finalStatus == ManusTaskStatus.COMPLETED) {
                    String generated = generateFinalReport(taskId);
                    if (StrUtil.isNotBlank(generated)) {
                        finalReport = generated;
                    }
                }
            }
            finishTask(taskId, context, finalStatus, finalReport, errorMessage);
        } catch (Exception e) {
            log.error("Manus 任务执行异常: taskId={}", taskId, e);
            finishTask(taskId, context, ManusTaskStatus.ERROR, agent == null ? null : agent.getLastAssistantText(), e.getMessage());
        } finally {
            runningContexts.remove(taskId);
        }
    }

    /**
     * 收尾任务：写入最终状态与 final 事件，推送并关闭全部订阅连接
     *
     * @param taskId       任务 ID
     * @param context      任务上下文
     * @param finalStatus  最终状态
     * @param finalReport  最终报告
     * @param errorMessage 失败原因（可为空）
     */
    private void finishTask(String taskId, ManusTaskContext context, ManusTaskStatus finalStatus,
                            String finalReport, String errorMessage) {
        Object lock = taskLocks.computeIfAbsent(taskId, k -> new Object());
        ManusTask.EventEntry finalEvent = new ManusTask.EventEntry();
        finalEvent.setType("final");
        finalEvent.setTimestamp(Instant.now().toString());
        finalEvent.setContent(buildFinalContent(finalStatus, finalReport, errorMessage));

        try {
            synchronized (lock) {
                ManusTask taskDoc = mongoTemplate.findById(taskId, ManusTask.class);
                if (taskDoc == null) {
                    return;
                }
                taskDoc.setStatus(finalStatus);
                // 停止收尾不覆写上一轮的实质成果（多轮追问场景下报告仍在展示）
                if (StrUtil.isBlank(finalReport)) {
                    finalReport = taskDoc.getFinalReport();
                }
                if (StrUtil.isBlank(errorMessage)) {
                    errorMessage = taskDoc.getErrorMessage();
                }
                // 任务正常完成时收尾计划状态：模型偶尔漏标 done，避免已完成任务里步骤一直显示"进行中"
                if (finalStatus == ManusTaskStatus.COMPLETED
                        && taskDoc.getPlan() != null && !taskDoc.getPlan().isEmpty()) {
                    for (ManusTask.PlanStep step : taskDoc.getPlan()) {
                        if ("in_progress".equals(step.getStatus()) || "todo".equals(step.getStatus())) {
                            // 进行中的步骤视为已完成，未开始的标记为跳过
                            step.setStatus("in_progress".equals(step.getStatus()) ? "done" : "skipped");
                            step.setNote(StrUtil.isBlank(step.getNote()) ? "（任务完成时自动收尾）" : step.getNote());
                        }
                    }
                }
                taskDoc.setFinalReport(finalReport);
                taskDoc.setErrorMessage(errorMessage);
                // 本轮有实质报告时按轮次归档，供后续追问轮做上下文重建
                if (finalStatus == ManusTaskStatus.COMPLETED && StrUtil.isNotBlank(finalReport)) {
                    if (taskDoc.getReports() == null) {
                        taskDoc.setReports(new ArrayList<>());
                    }
                    taskDoc.getReports().add(finalReport);
                }
                if (taskDoc.getEvents() == null) {
                    taskDoc.setEvents(new ArrayList<>());
                }
                taskDoc.getEvents().add(finalEvent);
                taskDoc.setUpdatedAt(Instant.now());
                mongoTemplate.save(taskDoc);
                broadcast(taskId, JSONUtil.toJsonStr(finalEvent));
            }
        } catch (Exception e) {
            // 收尾失败不能让异常逃出线程池（否则任务停留中间态且无人收尾），记录后仍关闭订阅连接
            log.error("Manus 任务收尾写入失败: taskId={}", taskId, e);
        } finally {
            completeEmitters(taskId);
        }
        log.info("Manus 任务结束: taskId={}, status={}", taskId, finalStatus);
    }

    /**
     * 发布事件：先落库（plan_updated 同时刷新计划快照），再推送给订阅连接
     * 落库与广播在同一把任务锁内，保证订阅回放与实时推送不重不漏
     *
     * @param taskId 任务 ID
     * @param entry  事件条目
     */
    private void publishEvent(String taskId, ManusTask.EventEntry entry) {
        Object lock = taskLocks.computeIfAbsent(taskId, k -> new Object());
        synchronized (lock) {
            ManusTask taskDoc = mongoTemplate.findById(taskId, ManusTask.class);
            if (taskDoc == null) {
                return;
            }
            if ("plan_updated".equals(entry.getType()) && entry.getSteps() != null) {
                taskDoc.setPlan(entry.getSteps());
            }
            if (taskDoc.getEvents() == null) {
                taskDoc.setEvents(new ArrayList<>());
            }
            taskDoc.getEvents().add(entry);
            taskDoc.setUpdatedAt(Instant.now());
            mongoTemplate.save(taskDoc);
            broadcast(taskId, JSONUtil.toJsonStr(entry));
        }
    }

    /**
     * 向任务的全部订阅连接推送事件
     * 调用方必须持有该任务的任务锁
     *
     * @param taskId 任务 ID
     * @param json   事件 JSON
     */
    private void broadcast(String taskId, String json) {
        List<SseEmitter> emitters = emitterMap.get(taskId);
        if (emitters == null || emitters.isEmpty()) {
            return;
        }
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(json);
            } catch (Exception e) {
                // 连接已断开：移除并结束连接（complete 幂等，onCompletion 回调会再清一次）
                emitters.remove(emitter);
                try {
                    emitter.complete();
                } catch (Exception ignored) {
                    // 连接已失效，忽略
                }
            }
        }
    }

    /**
     * 任务结束后关闭并清空该任务的全部订阅连接
     *
     * @param taskId 任务 ID
     */
    private void completeEmitters(String taskId) {
        List<SseEmitter> emitters = emitterMap.remove(taskId);
        if (emitters == null) {
            return;
        }
        for (SseEmitter emitter : emitters) {
            try {
                emitter.complete();
            } catch (Exception e) {
                log.debug("关闭 SSE 连接异常（忽略）: taskId={}", taskId, e);
            }
        }
    }

    /**
     * 移除单个订阅连接（连接完成回调）
     *
     * @param taskId  任务 ID
     * @param emitter SSE 连接
     */
    private void removeEmitter(String taskId, SseEmitter emitter) {
        List<SseEmitter> emitters = emitterMap.get(taskId);
        if (emitters != null) {
            emitters.remove(emitter);
        }
    }

    /**
     * 合成任务的工具集合：规划工具、交付物登记工具（每任务一份实例）+ 知识库检索工具
     * + 子任务派发工具（仅主智能体持有，子智能体白名单不含它以保证层级=1）+ 共享基础工具
     *
     * @param context 任务上下文
     * @return 工具回调数组
     */
    private ToolCallback[] mergeTools(ManusTaskContext context) {
        ToolCallback[] taskTools = ToolCallbacks.from(
                new PlanManagementTool(context), new RegisterDeliverableTool(context),
                knowledgeSearchTool, new SubAgentTool(context, subAgentRunner));
        ToolCallback[] merged = new ToolCallback[taskTools.length + allTools.length];
        System.arraycopy(taskTools, 0, merged, 0, taskTools.length);
        System.arraycopy(allTools, 0, merged, taskTools.length, allTools.length);
        return merged;
    }

    /**
     * 登记交付物：落库到任务文档并推送 deliverable 事件（由交付物登记工具经上下文回调）
     *
     * @param taskId      任务 ID
     * @param deliverable 交付物条目（工具侧已完成路径校验）
     */
    private void registerDeliverable(String taskId, ManusTask.Deliverable deliverable) {
        Object lock = taskLocks.computeIfAbsent(taskId, k -> new Object());
        ManusTask.EventEntry event = new ManusTask.EventEntry("deliverable", Instant.now().toString(),
                deliverable.getNote(), null, null, null, null, deliverable, "main");
        synchronized (lock) {
            ManusTask taskDoc = mongoTemplate.findById(taskId, ManusTask.class);
            if (taskDoc == null) {
                return;
            }
            if (taskDoc.getDeliverables() == null) {
                taskDoc.setDeliverables(new ArrayList<>());
            }
            taskDoc.getDeliverables().add(deliverable);
            if (taskDoc.getEvents() == null) {
                taskDoc.setEvents(new ArrayList<>());
            }
            taskDoc.getEvents().add(event);
            taskDoc.setUpdatedAt(Instant.now());
            mongoTemplate.save(taskDoc);
            broadcast(taskId, JSONUtil.toJsonStr(event));
        }
        log.info("Manus 任务登记交付物: taskId={}, name={}", taskId, deliverable.getName());
    }

    /**
     * 解析交付物文件：校验任务存在、序号有效、路径重新过白名单、文件存在
     *
     * @param taskId 任务 ID
     * @param index  交付物序号（1 开始，与登记顺序一致）
     * @return 交付物元数据与文件路径；任务或序号或文件不存在时返回 null
     */
    public DeliverableFile resolveDeliverableFile(String taskId, int index) {
        ManusTask taskDoc = getTask(taskId);
        if (taskDoc == null || taskDoc.getDeliverables() == null
                || index < 1 || index > taskDoc.getDeliverables().size()) {
            return null;
        }
        ManusTask.Deliverable deliverable = taskDoc.getDeliverables().get(index - 1);
        Path whitelistRoot = Paths.get(System.getProperty("user.dir"), "tmp").normalize();
        Path path = whitelistRoot.resolve(deliverable.getRelativePath()).normalize();
        // 二次白名单校验（Path 组件级比较，防 tmp2 等同级目录与 ../ 穿越），防止库内数据被篡改
        if (!path.startsWith(whitelistRoot) || path.equals(whitelistRoot)) {
            return null;
        }
        if (!Files.isRegularFile(path)) {
            return null;
        }
        return new DeliverableFile(deliverable, path);
    }

    /**
     * 交付物文件（元数据 + 磁盘路径）
     */
    public record DeliverableFile(ManusTask.Deliverable meta, Path path) {
    }

    /**
     * 收尾报告生成（结构性保证报告面向用户）
     * 取本轮（最后一个 user_message 之后）的执行记录摘要，做一次无工具的 LLM 调用生成最终报告；
     * 模型最后一条消息常是"现在调用 doTerminate"类过程独白，不能直接当报告
     *
     * @param taskId 任务 ID
     * @return 生成的报告文本；生成失败或无执行记录时返回 null（由调用方回退思考文本）
     */
    private String generateFinalReport(String taskId) {
        try {
            Object lock = taskLocks.computeIfAbsent(taskId, k -> new Object());
            String prompt;
            synchronized (lock) {
                ManusTask taskDoc = mongoTemplate.findById(taskId, ManusTask.class);
                if (taskDoc == null) {
                    return null;
                }
                prompt = buildReportPrompt(taskDoc);
            }
            if (prompt == null) {
                return null;
            }
            String report = buildChatClient()
                    .prompt()
                    .user(prompt)
                    .call()
                    .chatResponse()
                    .getResult()
                    .getOutput()
                    .getText();
            log.info("Manus 任务收尾报告生成完成: taskId={}, length={}", taskId, report == null ? 0 : report.length());
            return StrUtil.isBlank(report) ? null : report.trim();
        } catch (Exception e) {
            log.warn("Manus 任务收尾报告生成失败，回退为思考文本: taskId={}", taskId, e);
            return null;
        }
    }

    /**
     * 构建收尾报告生成的提示词（用户问题 + 本轮执行记录摘要）
     *
     * @param taskDoc 任务文档
     * @return 提示词；本轮无执行记录时返回 null
     */
    private String buildReportPrompt(ManusTask taskDoc) {
        List<ManusTask.EventEntry> events = taskDoc.getEvents();
        if (events == null || events.isEmpty()) {
            return null;
        }
        // 本轮起点：最后一个 user_message 之后（原始任务视为首轮起点，其问题单独取）
        int start = 0;
        String question = taskDoc.getTask();
        for (int i = events.size() - 1; i >= 0; i--) {
            if ("user_message".equals(events.get(i).getType())) {
                start = i + 1;
                question = events.get(i).getContent();
                break;
            }
        }
        if (start >= events.size()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("你是报告撰写员。以下是智能体执行任务时的问题与执行记录，请据此撰写一份面向用户的最终报告。\n");
        sb.append("要求：\n");
        sb.append("- 第一句直接给出答案或结论；\n");
        sb.append("- 报告只包含给用户看的内容（答案、结论、建议、数据），禁止出现\"根据执行记录\"\"智能体\"\"工具\"\"步骤\"等描述执行过程的语句；\n");
        sb.append("- 若执行中产出并登记了文件，报告说明产出了什么文件即可；禁止写\"如何获取/下载文件\"的操作指引（交付物卡片会自动展示在报告下方），也不要出现服务器本地路径；\n");
        sb.append("- 使用 Markdown 结构组织（结论先行、要点分条、信息完整）；\n");
        sb.append("- 若执行记录不足以完整回答问题，如实给出已获得的部分信息并说明未尽事项。\n\n");
        sb.append("【用户问题】\n").append(question).append("\n\n【执行记录】\n");
        for (int i = start; i < events.size(); i++) {
            ManusTask.EventEntry event = events.get(i);
            String detail = switch (StrUtil.nullToEmpty(event.getType())) {
                case "think" -> truncateText(event.getContent(), 300);
                case "tool_call" -> "调用 " + event.getToolName() + "，参数 " + StrUtil.nullToEmpty(event.getToolArgs());
                case "tool_result" -> event.getToolName() + " 返回：" + truncateText(event.getToolResult(), 500);
                case "deliverable" -> "产出文件 " + (event.getDeliverable() == null ? "" : event.getDeliverable().getName());
                default -> "";
            };
            if (StrUtil.isNotBlank(detail)) {
                sb.append("- [").append(event.getType()).append("] ").append(detail).append('\n');
            }
        }
        return sb.toString();
    }

    /**
     * 构建追问轮的初始消息（摘要重建上下文）
     * 包含原任务、历次追问、历轮报告、当前计划进度、最近事件摘要与本次追问
     *
     * @param taskDoc 任务文档
     * @return 重建后的初始用户消息
     */
    private String buildFollowUpContext(ManusTask taskDoc) {
        List<String> followUps = taskDoc.getFollowUps() == null ? new ArrayList<>() : taskDoc.getFollowUps();
        String currentMessage = followUps.get(followUps.size() - 1);
        StringBuilder sb = new StringBuilder();
        sb.append("【原任务】\n").append(taskDoc.getTask()).append("\n\n");
        if (followUps.size() > 1) {
            sb.append("【历次用户追问】\n");
            for (int i = 0; i < followUps.size() - 1; i++) {
                sb.append(i + 1).append(". ").append(truncateText(followUps.get(i), 500)).append('\n');
            }
        }
        if (taskDoc.getReports() != null && !taskDoc.getReports().isEmpty()) {
            sb.append("\n【历轮最终报告】\n");
            for (int i = 0; i < taskDoc.getReports().size(); i++) {
                sb.append("第 ").append(i + 1).append(" 轮：\n")
                        .append(truncateText(taskDoc.getReports().get(i), 2000)).append("\n\n");
            }
        }
        if (taskDoc.getPlan() != null && !taskDoc.getPlan().isEmpty()) {
            sb.append("【当前计划进度】\n");
            for (ManusTask.PlanStep step : taskDoc.getPlan()) {
                sb.append(step.getIndex()).append(". ").append(step.getContent())
                        .append("（").append(step.getStatus()).append("）\n");
            }
        }
        String recentEvents = buildRecentEventsDigest(taskDoc);
        if (!recentEvents.isEmpty()) {
            sb.append('\n').append(recentEvents).append('\n');
        }
        sb.append("\n【用户本次追问】\n").append(currentMessage);
        sb.append("\n\n请基于以上上下文执行用户的最新指令；如需继续执行任务，沿用规划工具纪律；完成后输出面向用户的最终总结并调用 doTerminate 结束。");
        return sb.toString();
    }

    /**
     * 构建最近执行事件的摘要（供追问轮了解此前发生了什么）
     *
     * @param taskDoc 任务文档
     * @return 事件摘要文本，无事件时返回空串
     */
    private String buildRecentEventsDigest(ManusTask taskDoc) {
        List<ManusTask.EventEntry> events = taskDoc.getEvents();
        if (events == null || events.isEmpty()) {
            return "";
        }
        int from = Math.max(0, events.size() - 10);
        StringBuilder sb = new StringBuilder("【最近执行事件摘要】\n");
        for (int i = from; i < events.size(); i++) {
            ManusTask.EventEntry event = events.get(i);
            String detail = switch (StrUtil.nullToEmpty(event.getType())) {
                case "think" -> truncateText(event.getContent(), 200);
                case "tool_call" -> event.getToolName() + " " + StrUtil.nullToEmpty(event.getToolArgs());
                case "tool_result" -> event.getToolName() + " → " + truncateText(event.getToolResult(), 200);
                case "user_message" -> truncateText(event.getContent(), 200);
                case "final" -> truncateText(event.getContent(), 300);
                default -> "";
            };
            if (StrUtil.isNotBlank(detail)) {
                sb.append("- [").append(event.getType()).append("] ").append(detail).append('\n');
            }
        }
        return sb.toString();
    }

    /**
     * 截断文本到指定长度
     *
     * @param text      原文本
     * @param maxLength 最大长度
     * @return 截断后的文本
     */
    private String truncateText(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return StrUtil.nullToEmpty(text);
        }
        return text.substring(0, maxLength) + "…（已截断）";
    }

    /**
     * 构建任务智能体使用的 ChatClient（与旧 Manus 相同：DashScope 模型 + 日志 Advisor）
     *
     * @return ChatClient
     */
    private ChatClient buildChatClient() {
        return ChatClient.builder(dashscopeChatModel)
                .defaultAdvisors(new MyLoggerAdvisor())
                .build();
    }

    /**
     * 构建 final 事件内容
     *
     * @param status       最终状态
     * @param finalReport  最终报告
     * @param errorMessage 失败原因
     * @return 面向用户的文本
     */
    private String buildFinalContent(ManusTaskStatus status, String finalReport, String errorMessage) {
        return switch (status) {
            case COMPLETED -> StrUtil.isBlank(finalReport) ? "任务已完成。" : finalReport;
            case STOPPED -> "任务已停止。";
            case ERROR -> "任务执行失败：" + (errorMessage != null ? errorMessage : "未知错误");
            default -> "任务已结束。";
        };
    }

    /**
     * 判断任务是否已到终态
     *
     * @param status 任务状态
     * @return 已结束返回 true
     */
    private boolean isFinished(ManusTaskStatus status) {
        return status == ManusTaskStatus.COMPLETED
                || status == ManusTaskStatus.STOPPED
                || status == ManusTaskStatus.ERROR;
    }

    /**
     * 任务文档转列表项
     *
     * @param taskDoc 任务文档
     * @return 列表项
     */
    private ManusTaskListItem toListItem(ManusTask taskDoc) {
        int totalSteps = taskDoc.getPlan() == null ? 0 : taskDoc.getPlan().size();
        int doneSteps = 0;
        if (taskDoc.getPlan() != null) {
            doneSteps = (int) taskDoc.getPlan().stream()
                    .filter(step -> "done".equals(step.getStatus()) || "skipped".equals(step.getStatus()))
                    .count();
        }
        return new ManusTaskListItem(taskDoc.getId(), taskDoc.getTitle(), taskDoc.getTask(),
                taskDoc.getStatus(), totalSteps, doneSteps, taskDoc.getCreatedAt(), taskDoc.getUpdatedAt());
    }

    /**
     * 创建任务执行线程（命名便于日志排查）
     *
     * @param r 线程体
     * @return 线程
     */
    private Thread newTaskThread(Runnable r) {
        Thread thread = new Thread(r, "manus-task-" + threadSeq.incrementAndGet());
        thread.setDaemon(true);
        return thread;
    }

    /**
     * 应用关闭时释放线程池
     */
    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }
}
