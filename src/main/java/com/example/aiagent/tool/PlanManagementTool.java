package com.example.aiagent.tool;

import cn.hutool.core.util.StrUtil;
import com.example.aiagent.agent.ManusTaskContext;
import com.example.aiagent.model.ManusTask;
import org.springframework.ai.tool.annotation.Tool;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 计划管理工具（Manus 超级智能体专用，每任务一份实例）
 * 对齐市面 ReAct 智能体的 PlanningTool：任务开始先创建计划，执行中逐步更新步骤状态
 * 状态变更通过 ManusTaskContext 发布 plan_updated 事件（服务层负责落库与 SSE 推送）
 */
public class PlanManagementTool {

    /**
     * 步骤状态的合法取值
     */
    private static final Set<String> VALID_STATUSES = Set.of("todo", "in_progress", "done", "skipped");

    /**
     * 计划步骤数量上限
     */
    private static final int MAX_STEPS = 20;

    private final ManusTaskContext context;

    public PlanManagementTool(ManusTaskContext context) {
        this.context = context;
    }

    /**
     * 创建（或重置）任务的执行计划
     *
     * @param steps 计划步骤内容列表，按执行顺序排列
     * @return 执行结果说明
     */
    @Tool(description = """
            Create the execution plan for the current task. You MUST call this tool at the very beginning \
            of a multi-step task to break it down into 3-8 concise, actionable steps listed in execution order. \
            If the task turns out to be different from expected, call this tool again to rebuild the plan.""")
    public String planCreate(List<String> steps) {
        if (steps == null || steps.isEmpty()) {
            return "错误：计划步骤不能为空，请提供至少一个步骤";
        }
        if (steps.size() > MAX_STEPS) {
            return "错误：计划步骤不能超过 " + MAX_STEPS + " 个，请合并或精简步骤";
        }
        List<ManusTask.PlanStep> plan = new ArrayList<>();
        for (int i = 0; i < steps.size(); i++) {
            String content = steps.get(i);
            if (StrUtil.isBlank(content)) {
                return "错误：第 " + (i + 1) + " 个步骤内容为空";
            }
            plan.add(new ManusTask.PlanStep(i + 1, content.trim(), "todo", null));
        }
        context.setPlan(plan);
        context.publish(new ManusTask.EventEntry("plan_updated", Instant.now().toString(),
                null, null, null, null, new ArrayList<>(plan)));
        return "计划已创建，共 " + plan.size() + " 步，请按顺序执行并逐步更新状态";
    }

    /**
     * 更新某个计划步骤的状态
     *
     * @param stepIndex 步骤序号，从 1 开始
     * @param status    目标状态：todo / in_progress / done / skipped
     * @param note      可选备注，说明进展或跳过原因
     * @return 执行结果说明
     */
    @Tool(description = """
            Update the status of one plan step. stepIndex starts from 1. \
            status must be one of: todo, in_progress, done, skipped. \
            Mark the current step as in_progress BEFORE working on it, and mark it as done AFTER finishing it. \
            An optional note can describe the progress or the reason for skipping.""")
    public String planUpdate(int stepIndex, String status, String note) {
        List<ManusTask.PlanStep> plan = context.getPlan();
        if (plan == null || plan.isEmpty()) {
            return "错误：尚未创建计划，请先调用 planCreate";
        }
        if (stepIndex < 1 || stepIndex > plan.size()) {
            return "错误：步骤序号 " + stepIndex + " 超出范围，有效范围是 1 到 " + plan.size();
        }
        if (!VALID_STATUSES.contains(status)) {
            return "错误：非法状态 " + status + "，只能是 todo / in_progress / done / skipped";
        }
        ManusTask.PlanStep step = plan.get(stepIndex - 1);
        step.setStatus(status);
        step.setNote(StrUtil.isBlank(note) ? step.getNote() : note.trim());
        context.publish(new ManusTask.EventEntry("plan_updated", Instant.now().toString(),
                null, null, null, null, new ArrayList<>(plan)));
        return "步骤 " + stepIndex + " 状态已更新为 " + status;
    }
}
