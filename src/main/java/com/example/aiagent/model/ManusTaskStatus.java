package com.example.aiagent.model;

/**
 * Manus 任务状态枚举
 */
public enum ManusTaskStatus {

    // 已创建，排队等待执行
    PENDING,

    // 执行中
    RUNNING,

    // 正常完成
    COMPLETED,

    // 用户主动停止
    STOPPED,

    // 执行出错
    ERROR
}
