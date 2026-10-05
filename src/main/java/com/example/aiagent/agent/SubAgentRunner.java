package com.example.aiagent.agent;

import cn.hutool.core.util.StrUtil;
import com.example.aiagent.agent.model.AgentState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallback;

import java.util.ArrayList;
import java.util.List;

/**
 * 子智能体执行器（agent-as-tool）
 * 组装受限工具集与角色提示词，驱动一个独立的 ManusTaskAgent 循环同步执行子任务并返回结果文本；
 * 与主任务共享停止标志（每步检查），层级限制由"白名单不含 delegate"保证，不与主执行循环互相嵌套
 */
@Slf4j
public class SubAgentRunner {

    /**
     * 子智能体结果的最大返回长度（超长截断，避免撑爆主智能体上下文）
     */
    private static final int MAX_RESULT_LENGTH = 6000;

    private final ToolCallback[] allTools;

    private final ChatClient subChatClient;

    private final int subMaxSteps;

    public SubAgentRunner(ToolCallback[] allTools, ChatClient subChatClient, int subMaxSteps) {
        this.allTools = allTools;
        this.subChatClient = subChatClient;
        this.subMaxSteps = Math.max(1, subMaxSteps);
    }

    /**
     * 同步执行子任务
     *
     * @param context 主任务上下文（共享停止标志与事件发布）
     * @param def     子智能体角色定义
     * @param task    子任务描述
     * @return 子智能体的结果文本（供主智能体汇总）；被停止或失败时返回相应说明
     */
    public String run(ManusTaskContext context, SubAgentCatalog.SubAgentDefinition def, String task) {
        List<ToolCallback> allowed = filterTools(def);
        if (allowed.isEmpty()) {
            return "子智能体「" + def.displayName() + "」没有可用工具，无法执行该子任务";
        }
        ManusTaskAgent agent = new ManusTaskAgent(allowed.toArray(new ToolCallback[0]), context);
        agent.setAgentName(def.key());
        agent.setName("sub-agent:" + def.key());
        agent.setSystemPrompt(def.systemPrompt());
        agent.setNextStepPrompt("围绕分配的子任务继续执行：收集或整理信息，完成后输出结果并调用 doTerminate 结束。");
        agent.setMaxSteps(subMaxSteps);
        agent.setChatClient(subChatClient);
        agent.getMessageList().add(new org.springframework.ai.chat.messages.UserMessage(task));

        log.info("子智能体开始执行: agent={}, steps<={}, task={}", def.key(), subMaxSteps,
                StrUtil.blankToDefault(task, "").substring(0, Math.min(30, task.length())));
        for (int i = 0; i < subMaxSteps; i++) {
            if (context.isStopRequested()) {
                return "子任务已随主任务停止而中止。";
            }
            agent.step();
            AgentState state = agent.getState();
            if (state == AgentState.FINISHED || state == AgentState.ERROR) {
                break;
            }
        }

        if (context.isStopRequested()) {
            return "子任务已随主任务停止而中止。";
        }
        if (agent.getState() == AgentState.ERROR) {
            return "子智能体「" + def.displayName() + "」执行出错：" + StrUtil.nullToEmpty(agent.getLastErrorContent());
        }
        String result = agent.getLastAssistantText();
        if (StrUtil.isBlank(result)) {
            return "子智能体「" + def.displayName() + "」未能产出有效结果（已达步骤上限），可根据需要调整子任务后重新派发。";
        }
        log.info("子智能体执行完成: agent={}, resultLength={}", def.key(), result.length());
        return truncate(result);
    }

    /**
     * 按角色白名单过滤共享工具（ToolDefinition.name 匹配），保持原顺序
     *
     * @param def 角色定义
     * @return 白名单内的工具回调
     */
    private List<ToolCallback> filterTools(SubAgentCatalog.SubAgentDefinition def) {
        List<ToolCallback> allowed = new ArrayList<>();
        for (ToolCallback callback : allTools) {
            String name = callback.getToolDefinition().name();
            if (def.toolNames().contains(name)) {
                allowed.add(callback);
            }
        }
        return allowed;
    }

    /**
     * 截断结果到最大返回长度
     *
     * @param text 原文本
     * @return 截断后的文本
     */
    private String truncate(String text) {
        if (text.length() <= MAX_RESULT_LENGTH) {
            return text;
        }
        return text.substring(0, MAX_RESULT_LENGTH) + "…（结果过长已截断）";
    }
}
