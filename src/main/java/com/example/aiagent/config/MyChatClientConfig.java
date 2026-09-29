package com.example.aiagent.config;


import com.example.aiagent.advisor.MyLoggerAdvisor;
import com.example.aiagent.chatmemory.MongoChatMemory;
import com.example.aiagent.constant.ChatModelCatalog;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * 多模型 ChatClient 配置：一个模型一个 Bean，Bean 名称与 ChatModelCatalog 中的模型标识一致
 * 同一底层 ChatModel 上的多个模型，各自用 defaultOptions 钉住模型名称
 * 由 ChatModelService 按 Bean 名称注入并解析，供前端切换模型使用
 */
@Configuration
public class MyChatClientConfig {

    /** 千问 OpenAI 兼容模式端点默认地址，可用 spring.ai.dashscope.compatible-base-url 覆盖 */
    private static final String DEFAULT_QWEN_BASE_URL = "https://dashscope.aliyuncs.com/compatible-mode";

    /** 日志拦截器 */
    private final MyLoggerAdvisor myLoggerAdvisor;

    /** 对话记忆（MongoDB），逐个 ChatClient 都要挂上，否则切换模型后丢失上下文 */
    private final MongoChatMemory mongoChatMemory;

    /** 系统提示词 */
    private final String systemPrompt;

    public MyChatClientConfig(MyLoggerAdvisor myLoggerAdvisor,
                              MongoChatMemory mongoChatMemory,
                              @Value("${knowledge-agent.system-prompt}") String systemPrompt) {
        this.myLoggerAdvisor = myLoggerAdvisor;
        this.mongoChatMemory = mongoChatMemory;
        this.systemPrompt = systemPrompt;
    }

    /**
     * 自定义 ChatModel 所使用的 Bean
     *
     * 千问对话模型（OpenAI 兼容模式）
     * 走 OpenAI 协议，与 DeepSeek 用同一套实现；密钥复用 spring.ai.dashscope.api-key，
     * 未配置时千问模型在清单中标记为不可用
     * 工具调用管理器使用 {@link ToolCallRepairingManager}，把该端点流式分片拆开的工具调用拼接完整
     *
     * @param apiKey  DashScope API Key
     * @param baseUrl 兼容模式端点地址，为空时使用默认地址
     * @return 千问对话模型
     */
    @Bean
    public ChatModel qwenChatModel(@Value("${spring.ai.dashscope.api-key:}") String apiKey,
                                  @Value("${spring.ai.dashscope.compatible-base-url:}") String baseUrl) {
        return OpenAiChatModel.builder()
                .openAiApi(OpenAiApi.builder()
                        .apiKey(apiKey)
                        .baseUrl(StringUtils.hasText(baseUrl) ? baseUrl : DEFAULT_QWEN_BASE_URL)
                        .build())
                .toolCallingManager(new ToolCallRepairingManager(ToolCallingManager.builder().build()))
                .build();
    }

    /**
     * DeepSeek V4.1 Flash 对话客户端
     *
     * @param chatModel DeepSeek 对话模型（spring.ai.openai.* 配置）
     * @return 配置好的 ChatClient
     */
    @Bean(ChatModelCatalog.DEEPSEEK_FLASH)
    public ChatClient chatClientDeepseekFlash(@Qualifier("openAiChatModel") ChatModel chatModel) {
        return buildChatClient(chatModel,
                OpenAiChatOptions.builder().model(ChatModelCatalog.DEEPSEEK_FLASH).build());
    }

    /**
     * DeepSeek V4 Pro 对话客户端
     *
     * @param chatModel DeepSeek 对话模型（spring.ai.openai.* 配置）
     * @return 配置好的 ChatClient
     */
    @Bean(ChatModelCatalog.DEEPSEEK_V4_PRO)
    public ChatClient chatClientDeepseekPro(@Qualifier("openAiChatModel") ChatModel chatModel) {
        return buildChatClient(chatModel,
                OpenAiChatOptions.builder().model(ChatModelCatalog.DEEPSEEK_V4_PRO).build());
    }

    /**
     * 千问 Qwen3.7 Plus 对话客户端
     *
     * @param chatModel 千问对话模型（OpenAI 兼容模式）
     * @return 配置好的 ChatClient
     */
    @Bean(ChatModelCatalog.QWEN_37_PLUS)
    public ChatClient chatClientQwen37Plus(@Qualifier("qwenChatModel") ChatModel chatModel) {
        return buildChatClient(chatModel,
                OpenAiChatOptions.builder().model(ChatModelCatalog.QWEN_37_PLUS).build());
    }

    /**
     * 千问 Qwen3.8 Flash 对话客户端
     *
     * @param chatModel 千问对话模型（OpenAI 兼容模式）
     * @return 配置好的 ChatClient
     */
    @Bean(ChatModelCatalog.QWEN_38_FLASH)
    public ChatClient chatClientQwen38Flash(@Qualifier("qwenChatModel") ChatModel chatModel) {
        return buildChatClient(chatModel,
                OpenAiChatOptions.builder().model(ChatModelCatalog.QWEN_38_FLASH).build());
    }

    /**
     * 构建统一的 ChatClient：钉住模型名称，并装配系统提示词、日志拦截器与对话记忆拦截器
     *
     * @param chatModel 底层对话模型
     * @param options   该模型的调用选项（指定模型名称）
     * @return 配置好的 ChatClient
     */
    private ChatClient buildChatClient(ChatModel chatModel, ChatOptions options) {
        return ChatClient.builder(chatModel)
                .defaultOptions(options)
                .defaultSystem(systemPrompt)
                .defaultAdvisors(myLoggerAdvisor, new MessageChatMemoryAdvisor(mongoChatMemory))
                .build();
    }
}
