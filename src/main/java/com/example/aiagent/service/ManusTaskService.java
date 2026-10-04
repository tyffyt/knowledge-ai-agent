package com.example.aiagent.service;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.example.aiagent.agent.ManusTaskAgent;
import com.example.aiagent.agent.ManusTaskContext;
import com.example.aiagent.agent.model.AgentState;
import com.example.aiagent.advisor.MyLoggerAdvisor;
import com.example.aiagent.model.ManusTask;
import com.example.aiagent.model.ManusTaskListItem;
import com.example.aiagent.model.ManusTaskStatus;
import com.example.aiagent.tool.PlanManagementTool;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbacks;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
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
            4. 所有步骤完成后，输出面向用户的完整结果总结，然后调用 doTerminate 工具结束任务；
            5. 如实汇报工具的执行结果，禁止编造结果。
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

    // 步骤上限，可通过 manus.agent.max-steps 配置
    @Value("${manus.agent.max-steps:20}")
    private int maxSteps;

    // 任务执行线程池（任务数量有限，固定 4 个线程足够）
    private final ExecutorService executor = Executors.newFixedThreadPool(4, this::newTaskThread);

    // 线程序号（线程命名用）
    private final AtomicInteger threadSeq = new AtomicInteger();

    // 每任务一把锁：同一任务的文档读改写与事件广播串行化，避免并发丢更新与事件重复
    private final Map<String, Object> taskLocks = new ConcurrentHashMap<>();

    // taskId -> 订阅中的 SSE 连接
    private final Map<String, List<SseEmitter>> emitterMap = new ConcurrentHashMap<>();

    // taskId -> 运行中任务上下文（用于停止请求）
    private final Map<String, ManusTaskContext> runningContexts = new ConcurrentHashMap<>();

    public ManusTaskService(MongoTemplate mongoTemplate, ToolCallback[] allTools, ChatModel dashscopeChatModel) {
        this.mongoTemplate = mongoTemplate;
        this.allTools = allTools;
        this.dashscopeChatModel = dashscopeChatModel;
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
     * 已有事件先回放；任务已结束时回放后立即关闭；执行中则保持长连接接收后续事件
     *
     * @param taskId 任务 ID
     * @return SSE 连接
     */
    public SseEmitter subscribe(String taskId) {
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
                    for (ManusTask.EventEntry entry : taskDoc.getEvents()) {
                        emitter.send(JSONUtil.toJsonStr(entry));
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
            // 排队中尚未开始：直接标记为停止
            latest.setStatus(ManusTaskStatus.STOPPED);
            latest.setUpdatedAt(Instant.now());
            mongoTemplate.save(latest);
            return "任务已停止（尚未开始执行）";
        }
    }

    /**
     * 执行任务（在线程池中运行）
     * 组装任务上下文、规划工具与任务智能体，循环执行直到完成/停止/出错
     *
     * @param taskId 任务 ID
     */
    private void executeTask(String taskId) {
        Object lock = taskLocks.computeIfAbsent(taskId, k -> new Object());
        ManusTaskContext context = new ManusTaskContext(taskId, entry -> publishEvent(taskId, entry));
        ManusTaskAgent agent = null;
        String taskText = null;
        try {
            synchronized (lock) {
                ManusTask taskDoc = mongoTemplate.findById(taskId, ManusTask.class);
                if (taskDoc == null || taskDoc.getStatus() == ManusTaskStatus.STOPPED) {
                    // 排队期间被停止或文档缺失：补收尾后不执行（保证订阅连接不悬挂）
                    if (taskDoc != null) {
                        finishTask(taskId, context, ManusTaskStatus.STOPPED, null, null);
                    }
                    return;
                }
                taskText = taskDoc.getTask();
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
            // 用户任务作为首条用户消息进入上下文（等价于 BaseAgent.run 的消息初始化）
            agent.getMessageList().add(new UserMessage(taskText));

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
                taskDoc.setFinalReport(finalReport);
                taskDoc.setErrorMessage(errorMessage);
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
     * 合成任务的工具集合：规划工具（每任务一份实例）+ 共享基础工具
     *
     * @param context 任务上下文
     * @return 工具回调数组
     */
    private ToolCallback[] mergeTools(ManusTaskContext context) {
        ToolCallback[] planTools = ToolCallbacks.from(new PlanManagementTool(context));
        ToolCallback[] merged = new ToolCallback[planTools.length + allTools.length];
        System.arraycopy(planTools, 0, merged, 0, planTools.length);
        System.arraycopy(allTools, 0, merged, planTools.length, allTools.length);
        return merged;
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
