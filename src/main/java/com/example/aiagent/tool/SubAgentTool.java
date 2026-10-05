package com.example.aiagent.tool;

import cn.hutool.core.util.StrUtil;
import com.example.aiagent.agent.ManusTaskContext;
import com.example.aiagent.agent.SubAgentCatalog;
import com.example.aiagent.agent.SubAgentRunner;
import org.springframework.ai.tool.annotation.Tool;

/**
 * 子任务派发工具（agent-as-tool，仅主智能体持有）
 * 把专业子任务派发给对应角色的子智能体同步执行并返回结果；
 * 参数校验在此完成，执行细节委托给 SubAgentRunner，工具类保持薄
 */
public class SubAgentTool {

    private final ManusTaskContext context;

    private final SubAgentRunner runner;

    public SubAgentTool(ManusTaskContext context, SubAgentRunner runner) {
        this.context = context;
        this.runner = runner;
    }

    /**
     * 派发子任务给指定角色的子智能体，同步等待其执行完成并返回结果
     *
     * @param agentName 子智能体角色标识（researcher / knowledgeResearcher / writer）
     * @param task      子任务描述（需自包含：目标、要点、期望产出，子智能体看不到主任务上下文）
     * @return 子智能体的执行结果文本
     */
    @Tool(description = """
            Delegate a self-contained sub-task to a specialized sub-agent and wait for its result. \
            Available agents: \
            researcher — deep web research (multi-keyword search, page reading, compiling findings); \
            knowledgeResearcher — deep retrieval and synthesis from the user's local knowledge base; \
            writer — organize given material into a document/PDF file and register it as a deliverable. \
            Rules: the task text must be self-contained (the sub-agent cannot see the main task); \
            delegate ONLY when the sub-task needs focused, multi-step specialist work — do simple steps yourself; \
            after receiving the result, integrate it yourself; do not delegate the same task twice.""")
    public String delegate(String agentName, String task) {
        SubAgentCatalog.SubAgentDefinition def = SubAgentCatalog.find(agentName);
        if (def == null) {
            return "错误：未知的子智能体角色 " + agentName + "，可用角色：researcher / knowledgeResearcher / writer";
        }
        if (StrUtil.isBlank(task)) {
            return "错误：子任务描述不能为空，且必须自包含（子智能体看不到主任务上下文）";
        }
        return runner.run(context, def, task.trim());
    }
}
