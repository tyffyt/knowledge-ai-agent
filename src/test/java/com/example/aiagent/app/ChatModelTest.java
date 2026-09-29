package com.example.aiagent.app;

import com.example.aiagent.constant.ChatModelCatalog;
import com.example.aiagent.service.ChatModelService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

public class ChatModelTest {
    @Autowired
    private ChatModelService chatModelService;

    @Autowired
    private ToolCallback[] allTools;

    @Value("${knowledge-agent.system-prompt}")
    private String SYSTEM_PROMPT;

    @Test
    void contextLoads() {
        ChatClient chatClient = chatModelService.resolveChatClient(ChatModelCatalog.QWEN_38_FLASH);
        System.out.println(chatClient.prompt().system(SYSTEM_PROMPT).user("你是谁？").tools(allTools).call().chatResponse().getResult().getOutput().getText());
    }
}
