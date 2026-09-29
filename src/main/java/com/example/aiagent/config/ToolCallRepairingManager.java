package com.example.aiagent.config;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 工具调用管理器：执行前把流式分片拆开的工具调用拼接完整，再交给默认实现执行
 * 千问（DashScope 兼容模式）的流式分片会把一个工具调用拆成「有名称无参数」与「无名称有参数」两条，
 * 本类按出现顺序把无名称分片的参数并回前一条有名称的调用；始终无名称的调用无法定位工具，直接丢弃
 */
public class ToolCallRepairingManager implements ToolCallingManager {

    /** 无参数工具在参数为空时使用的默认参数 */
    private static final String EMPTY_ARGUMENTS = "{}";

    /** 实际执行工具调用的默认实现 */
    private final ToolCallingManager delegate;

    public ToolCallRepairingManager(ToolCallingManager delegate) {
        this.delegate = delegate;
    }

    /**
     * 解析工具定义，直接交由默认实现
     *
     * @param chatOptions 携带工具配置的调用选项
     * @return 工具定义列表
     */
    @Override
    public List<ToolDefinition> resolveToolDefinitions(ToolCallingChatOptions chatOptions) {
        return delegate.resolveToolDefinitions(chatOptions);
    }

    /**
     * 拼接工具调用后执行
     *
     * @param prompt       本轮提示词
     * @param chatResponse 模型返回的响应
     * @return 工具执行结果
     */
    @Override
    public ToolExecutionResult executeToolCalls(Prompt prompt, ChatResponse chatResponse) {
        return delegate.executeToolCalls(prompt, repairToolCalls(chatResponse));
    }

    /**
     * 重建响应，把被拆开的工具调用拼接完整；无需改动时原样返回
     *
     * @param response 模型返回的响应
     * @return 修复后的响应
     */
    private ChatResponse repairToolCalls(ChatResponse response) {
        if (response == null || response.getResults() == null || response.getResults().isEmpty()) {
            return response;
        }
        List<Generation> generations = new ArrayList<>(response.getResults().size());
        boolean repaired = false;
        for (Generation generation : response.getResults()) {
            AssistantMessage message = generation.getOutput();
            List<AssistantMessage.ToolCall> toolCalls = message == null ? null : message.getToolCalls();
            if (toolCalls == null || toolCalls.isEmpty()) {
                generations.add(generation);
                continue;
            }
            List<AssistantMessage.ToolCall> merged = mergeFragments(toolCalls);
            if (merged.equals(toolCalls)) {
                generations.add(generation);
                continue;
            }
            generations.add(new Generation(
                    new AssistantMessage(message.getText(), message.getMetadata(), merged),
                    generation.getMetadata()));
            repaired = true;
        }
        return repaired ? new ChatResponse(generations, response.getMetadata()) : response;
    }

    /**
     * 按出现顺序拼接工具调用：有名称的调用吸收紧随其后所有无名称分片的参数
     * 无名称且未被吸收的调用直接丢弃
     *
     * @param toolCalls 原始工具调用列表
     * @return 拼接后的工具调用列表
     */
    private List<AssistantMessage.ToolCall> mergeFragments(List<AssistantMessage.ToolCall> toolCalls) {
        List<AssistantMessage.ToolCall> merged = new ArrayList<>(toolCalls.size());
        for (int i = 0; i < toolCalls.size(); i++) {
            AssistantMessage.ToolCall call = toolCalls.get(i);
            if (!StringUtils.hasText(call.name())) {
                continue;
            }
            StringBuilder arguments = new StringBuilder(StringUtils.hasText(call.arguments()) ? call.arguments() : "");
            while (i + 1 < toolCalls.size() && !StringUtils.hasText(toolCalls.get(i + 1).name())) {
                String fragment = toolCalls.get(i + 1).arguments();
                if (fragment != null) {
                    arguments.append(fragment);
                }
                i++;
            }
            merged.add(new AssistantMessage.ToolCall(
                    call.id(), call.type(), call.name(),
                    arguments.length() > 0 ? arguments.toString() : EMPTY_ARGUMENTS));
        }
        return merged;
    }
}
