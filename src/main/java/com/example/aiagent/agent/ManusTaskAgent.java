package com.example.aiagent.agent;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.example.aiagent.agent.model.AgentState;
import com.example.aiagent.model.ManusTask;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.ToolCallback;

import java.time.Instant;
import java.util.List;

/**
 * Manus 任务智能体（ReAct 模式，事件化）
 * 复用 ToolCallAgent 的工具调用骨架（构造器、DashScope 选项与 act 执行逻辑），不改动旧类；
 * 在 think/act 前后发布结构化事件（think / tool_call / tool_result / error），
 * 由 ManusTaskContext 交给服务层落库并推送 SSE，任务停止请求在步骤间生效
 */
@Slf4j
public class ManusTaskAgent extends ToolCallAgent {

    /**
     * 思考连续失败的最大次数，达到后置为 ERROR 结束任务，避免无意义空转到步骤上限
     */
    private static final int MAX_THINK_FAILURES = 3;

    /**
     * 事件中工具结果的最大保留长度（完整结果仍保留在消息上下文中供模型使用）
     */
    private static final int MAX_TOOL_RESULT_LENGTH = 4000;

    /**
     * 事件中工具调用参数的最大保留长度
     */
    private static final int MAX_TOOL_ARGS_LENGTH = 2000;

    private final ManusTaskContext context;

    // 最后一次无工具调用的思考文本（作为最终报告候选）
    private String lastAssistantText;

    // 最近一次思考失败的错误信息
    private String lastErrorContent;

    // 思考连续失败计数（成功一次即清零）
    private int consecutiveThinkFailures = 0;

    public ManusTaskAgent(ToolCallback[] availableTools, ManusTaskContext context) {
        super(availableTools);
        this.context = context;
    }

    /**
     * 思考：调用大模型决定下一步行动，并发布 think / tool_call 事件
     *
     * @return 是否需要执行行动
     */
    @Override
    public boolean think() {
        if (StrUtil.isNotBlank(getNextStepPrompt())) {
            getMessageList().add(new UserMessage(getNextStepPrompt()));
        }
        List<Message> messageList = getMessageList();
        Prompt prompt = new Prompt(messageList, getChatOptions());
        try {
            ChatResponse chatResponse = getChatClient().prompt(prompt)
                    .system(getSystemPrompt())
                    .tools(getAvailableTools())
                    .call()
                    .chatResponse();
            // 记录响应，供 act 执行工具调用
            setToolCallChatResponse(chatResponse);
            AssistantMessage assistantMessage = chatResponse.getResult().getOutput();
            List<AssistantMessage.ToolCall> toolCallList = assistantMessage.getToolCalls();
            String text = assistantMessage.getText();
            if (StrUtil.isNotBlank(text)) {
                context.publish(new ManusTask.EventEntry("think", Instant.now().toString(),
                        text, null, null, null, null));
                // 记录最近一次有效思考文本作为最终报告候选（最终总结常与 doTerminate 在同一响应返回）
                this.lastAssistantText = text;
            }
            log.info("{} 思考：{}，选择了 {} 个工具", getName(), text, toolCallList.size());
            if (toolCallList.isEmpty()) {
                // 不调用工具：手动记录助手消息
                getMessageList().add(assistantMessage);
                consecutiveThinkFailures = 0;
                return false;
            }
            for (AssistantMessage.ToolCall toolCall : toolCallList) {
                context.publish(new ManusTask.EventEntry("tool_call", Instant.now().toString(),
                        null, toolCall.name(), truncate(toolCall.arguments(), MAX_TOOL_ARGS_LENGTH),
                        null, null));
            }
            consecutiveThinkFailures = 0;
            return true;
        } catch (Exception e) {
            log.error("{} 思考过程出现异常", getName(), e);
            lastErrorContent = e.getMessage();
            consecutiveThinkFailures++;
            if (consecutiveThinkFailures >= MAX_THINK_FAILURES) {
                setState(AgentState.ERROR);
                context.publish(new ManusTask.EventEntry("error", Instant.now().toString(),
                        "思考连续失败 " + MAX_THINK_FAILURES + " 次，任务中止：" + e.getMessage(),
                        null, null, null, null));
            } else {
                context.publish(new ManusTask.EventEntry("error", Instant.now().toString(),
                        "本次思考失败，将自动重试：" + e.getMessage(), null, null, null, null));
            }
            getMessageList().add(new AssistantMessage("处理时遇到了错误：" + e.getMessage()));
            return false;
        }
    }

    /**
     * 行动：执行工具调用并发布 tool_result 事件
     *
     * @return 执行结果摘要
     */
    @Override
    public String act() {
        if (!getToolCallChatResponse().hasToolCalls()) {
            return "没有工具需要调用";
        }
        try {
            String result = super.act();
            Message last = CollUtil.getLast(getMessageList());
            if (last instanceof ToolResponseMessage toolResponseMessage) {
                for (ToolResponseMessage.ToolResponse response : toolResponseMessage.getResponses()) {
                    context.publish(new ManusTask.EventEntry("tool_result", Instant.now().toString(),
                            null, response.name(), null,
                            truncate(response.responseData(), MAX_TOOL_RESULT_LENGTH), null));
                }
            }
            return result;
        } catch (Exception e) {
            log.error("{} 工具执行出现异常", getName(), e);
            context.publish(new ManusTask.EventEntry("error", Instant.now().toString(),
                    "工具执行失败：" + e.getMessage(), null, null, null, null));
            return "工具执行失败：" + e.getMessage();
        }
    }

    public String getLastAssistantText() {
        return lastAssistantText;
    }

    public String getLastErrorContent() {
        return lastErrorContent;
    }

    /**
     * 截断文本到指定长度（超长部分以省略标记结尾）
     *
     * @param text      原文本
     * @param maxLength 最大长度
     * @return 截断后的文本
     */
    private String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "…（已截断）";
    }
}
