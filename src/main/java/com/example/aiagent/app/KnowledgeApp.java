package com.example.aiagent.app;

import cn.hutool.json.JSONUtil;
import com.example.aiagent.advisor.DocCaptureAdvisor;
import com.example.aiagent.advisor.MyLoggerAdvisor;
import com.example.aiagent.chatmemory.MongoChatMemory;
import com.example.aiagent.model.ChatMessages;
import com.example.aiagent.rag.KnowledgeAppRagCustomAdvisorFactory;
import com.example.aiagent.rag.QueryRewriter;
import com.example.aiagent.service.ChatModelService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.QuestionAnswerAdvisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor.CHAT_MEMORY_CONVERSATION_ID_KEY;
import static org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor.CHAT_MEMORY_RETRIEVE_SIZE_KEY;

@Component
@Slf4j
public class KnowledgeApp {

    // AI 回复中的知识库引用标注，如 [1]、[2]（1-2 位数字，避免误匹配年份如 [2026]）
    private static final Pattern CITATION_PATTERN = Pattern.compile("\\[\\d{1,2}\\]");

    // ==================== 模式一（备用）：单一 ChatClient，模型固定为 DeepSeek ====================
    // private final ChatClient chatClient;

    // ==================== 模式二（当前）：多模型切换 ====================
    // ChatClient 由 config/MyChatClientConfig 按模型提供，按模型标识解析
    private final ChatModelService chatModelService;

    // 构造函数注入系统提示词
    private final String SYSTEM_PROMPT;
    @Resource
    private ToolCallback[] allTools;
    // 知识库向量存储（基于内存的RAG）
    @Resource
    private VectorStore knowledgeVectorStore;
    // 查询重写器
    @Resource
    private QueryRewriter queryRewriter;
    // MongoDB 操作（用于持久化 RAG 引用）
    @Resource
    private MongoTemplate mongoTemplate;
    // MCP服务工具调用
    @Resource
    private ToolCallbackProvider toolCallbackProvider;


    // ==================== 模式一（备用）：单一模型，固定 DeepSeek ====================
    // 切回模式一的步骤：① 放开本构造器与上面的 chatClient 字段；
    //                  ② 注释掉下面的模式二构造器；
    //                  ③ 放开 resolveChatClient 中模式一的 return
    // 注意：模式一下前端传入的模型标识不生效，模型下拉框不会真的切换模型
    // MongoChatMemory,构造器注入，因为@Resource属于属性注入，晚于构造器
    // public KnowledgeApp(@Qualifier("openAiChatModel") ChatModel chatModel, MongoChatMemory mongoChatMemory, @Value("${knowledge-agent.system-prompt}") String SYSTEM_PROMPT) {
    //     // 注入提示词
    //     this.SYSTEM_PROMPT = SYSTEM_PROMPT;
    //
    //     // // 初始化基于文件的对话记忆
    //     // String fileDir = System.getProperty("user.dir") + "\\chat_memory";
    //     // ChatMemory chatMemory = new FileBasedChatMemory(fileDir);
    //
    //     chatClient = ChatClient.builder(chatModel)
    //             .defaultSystem(SYSTEM_PROMPT)
    //             .defaultAdvisors(
    //                     // 基于内存
    //                     // new MessageChatMemoryAdvisor(chatMemory),
    //                     // 基于MongoDB
    //                     new MessageChatMemoryAdvisor(mongoChatMemory),
    //                     // 自定义日志拦截器
    //                     new MyLoggerAdvisor()
    //             ).build();
    //
    // }

    /**
     * 模式二（当前使用）：多模型切换
     * ChatClient 由 config/MyChatClientConfig 按模型提供，通过 ChatModelService 按模型标识解析
     * 对话记忆由各 ChatClient 上的 MessageChatMemoryAdvisor 负责，与模型无关，切换模型后上下文依然连续
     *
     * @param chatModelService 模型清单与解析服务
     * @param SYSTEM_PROMPT    系统提示词，来自配置文件
     */
    public KnowledgeApp(ChatModelService chatModelService,
                        @Value("${knowledge-agent.system-prompt}") String SYSTEM_PROMPT) {
        this.chatModelService = chatModelService;
        this.SYSTEM_PROMPT = SYSTEM_PROMPT;
    }

    /**
     * 获取当前使用的 ChatClient，是两种模式的唯一取用点
     * 模式二（当前）：按模型标识解析，支持多模型切换；标识为空时使用默认模型
     * 模式一（备用）：忽略 model 参数，固定使用构造器里创建的单一 ChatClient
     *
     * @param model 模型标识，可为空
     * @return 对应的 ChatClient
     */
    private ChatClient resolveChatClient(String model) {
        // 模式二（当前）
        return chatModelService.resolveChatClient(model);
        // 模式一（备用）
        // return this.chatClient;
    }

    /**
     * 同步调用知识助手应用，使用默认模型
     *
     * @param message 用户输入的消息内容
     * @param chatId  聊天会话的唯一标识符
     * @return AI 回复内容
     */
    public String doChat(String message, String chatId) {
        return doChat(message, chatId, null);
    }

    /**
     * 同步调用知识助手应用
     *
     * @param message 用户输入的消息内容
     * @param chatId  聊天会话的唯一标识符
     * @param model   模型标识，为空时使用默认模型
     * @return AI 回复内容
     */
    public String doChat(String message, String chatId, String model) {
        ChatResponse response = resolveChatClient(model)
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId)
                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10))
                .call()
                .chatResponse();
        String content = response.getResult().getOutput().getText();
        log.info("content: {}", content);
        return content;
    }

    /**
     * 通过流式方式处理聊天请求 （无RAG），使用默认模型
     *
     * @param message 用户输入的消息内容
     * @param chatId  聊天会话的唯一标识符
     * @return 返回一个Flux流，包含流式返回的聊天响应内容
     */
    public Flux<String> doChatByStream(String message, String chatId) {
        return doChatByStream(message, chatId, null);
    }

    /**
     * 通过流式方式处理聊天请求 （无RAG）
     *
     * @param message 用户输入的消息内容
     * @param chatId  聊天会话的唯一标识符
     * @param model   模型标识，为空时使用默认模型
     * @return 返回一个Flux流，包含流式返回的聊天响应内容
     */
    public Flux<String> doChatByStream(String message, String chatId, String model) {
        return resolveChatClient(model)
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId)
                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10))
                // .advisors(new QuestionAnswerAdvisor(knowledgeVectorStore))
                .tools(allTools)
                .stream()
                .content()
                // 对话结束后刷新会话修改时间
                .doOnComplete(() -> touchUpdatedAt(chatId));
    }

    /**
     * 带引用标注的 RAG 流式对话
     * AI 回复中会用 [1]、[2] 标注引用来源
     * 流结束后追加引用切片信息，前端可解析展示
     * 通过 LLM 智能判断是否需要检索知识库
     *
     * @param message 用户输入的消息内容
     * @param chatId  聊天会话的唯一标识符
     * @param model   模型标识，为空时使用默认模型
     * @return 返回一个Flux流，包含流式返回的聊天响应内容与末尾的引用切片信息
     */
    public Flux<String> doChatByStreamWithRag(String message, String chatId, String model) {
        // 先解析模型：模型非法或不可用时立即失败，不再触发后面的内部 LLM 调用
        ChatClient chatClient = resolveChatClient(model);

        // RAG向量数据库检索设置（相似度阈值 0.5，topK 3）
        QuestionAnswerAdvisor ragAdvisor = QuestionAnswerAdvisor.builder(this.knowledgeVectorStore)
                .searchRequest(SearchRequest.builder().similarityThreshold(0.5d).topK(3).build())
                .build();

        // 智能分析：判断是否需要检索 + 查询改写（一次LLM调用完成）
        // 该内部调用固定使用默认模型，不随用户选择的模型变化
        QueryRewriter.QueryAnalysis analysis = queryRewriter.analyze(message);
        log.info("查询分析: needsRetrieval={}, rewrittenQuery={}", analysis.needsRetrieval(), analysis.rewrittenQuery());

        // 不需要检索知识库：直接走普通流式对话
        if (!analysis.needsRetrieval()) {
            log.info("智能判断为无需检索知识库: message={}", message);
            return doChatByStream(message, chatId, model);
        }

        // 需要检索：使用改写后的查询进行 RAG 流式对话
        String rewriteMessage = analysis.rewrittenQuery();
        // 若重写的内容为空，则为不需要重写，将用户原输入作为用户提示词
        if (rewriteMessage == null || rewriteMessage.isBlank()) {
            rewriteMessage = message;
        }

        // 文档捕获 Advisor
        DocCaptureAdvisor docCaptureAdvisor = new DocCaptureAdvisor();

        // 引用标注指令：追加到系统提示词中
        String citationInstruction = "\n\n【引用规范】当你引用知识库中的内容时，"
                + "请在引用内容的结尾处标注来源编号，格式为 [1]、[2] 等。"
                + "例如：根据你整理的笔记，知识管理的核心在于持续积累与分类[1]。"
                + "注意：只有问题涉及知识库相关内容时才引用，日常问候或无关问题无需引用。";

        // 累积流式回复内容，用于判断 AI 是否实际引用了知识库内容
        StringBuilder streamedContent = new StringBuilder();
        Flux<String> contentFlux = chatClient
                .prompt()
                .user(rewriteMessage)
                .system(s -> s.text(SYSTEM_PROMPT + citationInstruction))
                .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId)
                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10))
                .advisors(ragAdvisor, docCaptureAdvisor)
                .tools(allTools)
                .stream()
                .content()
                .doOnNext(streamedContent::append);

        // 文本流结束后，追加引用切片信息并持久化到 MongoDB
        // 仅当检索到内容且 AI 回复中实际引用了知识库（含 [n] 标注）时才展示引用，
        // 避免 AI 明确说"没有检索到数据"时引用标注仍然显示
        return contentFlux.concatWith(Flux.defer(() -> {
            List<Document> docs = docCaptureAdvisor.getRetrievedDocuments();
            if (docs != null && !docs.isEmpty() && containsCitation(streamedContent.toString())) {
                List<Map<String, Object>> refs = new ArrayList<>();
                for (int i = 0; i < docs.size(); i++) {
                    Document doc = docs.get(i);
                    refs.add(Map.of(
                            "index", i + 1,
                            "content", doc.getText(),
                            "metadata", doc.getMetadata()
                    ));
                }
                // 持久化 references 到 MongoDB（ChatMessages 的最后一条 assistant 消息）
                try {
                    Query query = new Query(Criteria.where("conversationId").is(chatId));
                    ChatMessages chatDoc = mongoTemplate.findOne(query, ChatMessages.class, "chat_memory");
                    if (chatDoc != null && chatDoc.getMessages() != null && !chatDoc.getMessages().isEmpty()) {
                        // 最后一条消息是 AI 的回复
                        ChatMessages.MessageDocument lastMsg = chatDoc.getMessages().get(chatDoc.getMessages().size() - 1);
                        lastMsg.setReferences(refs);
                        chatDoc.setUpdatedAt(Instant.now());
                        mongoTemplate.save(chatDoc, "chat_memory");
                        log.info("RAG 引用已持久化到 MongoDB: chatId={}, refs={}", chatId, refs.size());
                    }
                } catch (Exception e) {
                    log.error("持久化 RAG 引用失败", e);
                }

                String refsJson = JSONUtil.toJsonStr(refs);
                return Flux.just("\n\n<!--RAG_REFS-->" + refsJson);
            }
            log.info("RAG 未检索到相关文档或回复未引用知识库内容");
            return Flux.empty();
        // 对话结束后刷新会话修改时间
        })).doOnComplete(() -> touchUpdatedAt(chatId));
    }

    /**
     * 判断 AI 回复是否实际引用了知识库内容（回复中包含 [1]、[2] 等引用标注）
     *
     * @param content AI 回复的完整文本
     * @return 是否包含引用标注
     */
    private boolean containsCitation(String content) {
        if (content == null || content.isBlank()) {
            return false;
        }
        return CITATION_PATTERN.matcher(content).find();
    }

    /**
     * 刷新会话修改时间（对话结束后调用，用于历史会话按修改时间排序）
     *
     * @param chatId 聊天会话的唯一标识符
     */
    private void touchUpdatedAt(String chatId) {
        try {
            Query query = new Query(Criteria.where("conversationId").is(chatId));
            ChatMessages chatDoc = mongoTemplate.findOne(query, ChatMessages.class, "chat_memory");
            if (chatDoc != null) {
                chatDoc.setUpdatedAt(Instant.now());
                mongoTemplate.save(chatDoc, "chat_memory");
            }
        } catch (Exception e) {
            log.error("刷新会话修改时间失败: chatId={}", chatId, e);
        }
    }

    /**
     * KnowledgeReport 是一个记录知识总结的记录类(Record)
     * 用于存储知识回答的标题和相关摘要列表
     *
     * @param title       报告的标题，类型为String
     * @param suggestions 知识摘要列表，类型为List<String>
     */
    record KnowledgeReport(String title, List<String> suggestions) {

    }

    /**
     * AI 知识总结功能（结构化输出）
     *
     * @param message
     * @param chatId
     * @return
     */
    public KnowledgeReport doChatWithReport(String message, String chatId) {
        KnowledgeReport response = resolveChatClient(null)
                .prompt()
                .system(SYSTEM_PROMPT + "每次对话后都要生成知识总结，标题为{用户名}的知识报告，内容为要点列表")
                .user(message)
                .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId)
                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10))
                .call()
                .entity(KnowledgeReport.class);
        log.info("KnowledgeReport: {}", response);
        return response;
    }

    /**
     * 用 RAG 向量知识库进行对话 （暂不使用该RAG）
     *
     * @param message
     * @param chatId
     * @return
     */
    // @Autowired
    // private VectorStore pgVectorVectorStore;
    public String doChatWithRag(String message, String chatId) {
        // 查询分析（重写），如果重写的内容为空，则为不需要重写，将用户原输入作为用户提示词
        QueryRewriter.QueryAnalysis analysis = queryRewriter.analyze(message);
        String rewriteMessage = (analysis.rewrittenQuery() != null && !analysis.rewrittenQuery().isBlank())
                ? analysis.rewrittenQuery() : message;

        ChatResponse chatResponse = resolveChatClient(null)
                .prompt()
                // 使用改写后的查询
                .user(rewriteMessage)
                .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId)
                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10))
                // 应用 RAG 知识库问答（基于 本地内存 向量数据库）
                // .advisors(new QuestionAnswerAdvisor(knowledgeVectorStore))
                // 应用 RAG 检索增强服务（基于 PGVector 向量存储）
                // .advisors(new QuestionAnswerAdvisor(pgVectorVectorStore))
                // 带有标签过滤的 RAG 增强服务
                .advisors(
                        KnowledgeAppRagCustomAdvisorFactory.createKnowledgeRagAdvisor(
                                knowledgeVectorStore, "知识库"
                        )
                )
                .call()
                .chatResponse();
        String content = chatResponse.getResult().getOutput().getText();
        log.info("content: {}", content);
        return content;
    }

    /**
     * AI 工具对话功能（支持调用工具）
     */
    public String doChatWithTool(String message, String chatId) {
        ChatResponse chatResponse = resolveChatClient(null)
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId)
                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10))
                // 开启日志
                .advisors(new MyLoggerAdvisor())
                .tools(allTools)
                .call()
                .chatResponse();
        String content = chatResponse.getResult().getOutput().getText();
        log.info("content: {}", content);
        return content;
    }

    /**
     * AI 对话功能（调用MCP服务）
     */
    public String doChatWithMcp(String message, String chatId) {
        ChatResponse chatResponse = resolveChatClient(null)
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId)
                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10))
                // 开启日志
                .advisors(new MyLoggerAdvisor())
                .tools(toolCallbackProvider)
                .call()
                .chatResponse();
        String content = chatResponse.getResult().getOutput().getText();
        log.info("content: {}", content);
        return content;
    }
}
