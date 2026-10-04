package com.example.aiagent.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Manus 任务列表项（轻量 DTO，不含事件日志与计划明细，避免列表接口传输过重）
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ManusTaskListItem {

    // 任务 ID
    private String id;

    // 展示标题
    private String title;

    // 任务描述
    private String task;

    // 任务状态
    private ManusTaskStatus status;

    // 计划总步数（未创建计划时为 0）
    private int totalSteps;

    // 已完成步数
    private int doneSteps;

    private Instant createdAt;

    private Instant updatedAt;
}
