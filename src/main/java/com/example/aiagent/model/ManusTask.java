package com.example.aiagent.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Manus 超级智能体任务文档
 * 存储于 chat_memory_db 库的 manus_task 集合（manus_ 前缀与 chat_memory 集合区分）
 * 一个任务一份文档：任务描述、计划步骤、执行事件日志、最终报告
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Document("manus_task")
public class ManusTask {

    @Id
    private String id;

    // 列表展示标题（创建时截取任务描述前 20 字）
    private String title;

    // 用户原始任务描述
    private String task;

    // 任务状态
    private ManusTaskStatus status;

    // 计划步骤清单（由 PlanManagementTool 维护，plan_updated 事件同步更新）
    private List<PlanStep> plan = new ArrayList<>();

    // 执行事件日志（结构化事件按时间追加，用于实时推送与回放）
    private List<EventEntry> events = new ArrayList<>();

    // 最终报告（任务完成时的总结文本）
    private String finalReport;

    // 失败原因（状态为 ERROR 时记录）
    private String errorMessage;

    private Instant createdAt;

    private Instant updatedAt;

    /**
     * 计划步骤
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PlanStep {

        // 步骤序号，从 1 开始
        private int index;

        // 步骤内容
        private String content;

        // 步骤状态：todo（待执行）/ in_progress（进行中）/ done（已完成）/ skipped（已跳过）
        private String status;

        // 步骤备注（planUpdate 时可选携带）
        private String note;
    }

    /**
     * 执行事件条目
     * type 取值：plan_updated / think / tool_call / tool_result / final / error
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EventEntry {

        // 事件类型
        private String type;

        // 事件时间（ISO-8601 字符串，前端可直接 new Date 解析）
        private String timestamp;

        // 文本内容（think 的思考文本 / final 的最终报告 / error 的错误信息）
        private String content;

        // 工具名称（tool_call / tool_result）
        private String toolName;

        // 工具调用参数 JSON（tool_call）
        private String toolArgs;

        // 工具返回结果（tool_result，超长截断）
        private String toolResult;

        // 全量计划快照（plan_updated）
        private List<PlanStep> steps;
    }
}
