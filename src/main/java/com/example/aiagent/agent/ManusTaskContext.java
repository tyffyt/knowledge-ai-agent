package com.example.aiagent.agent;

import com.example.aiagent.model.ManusTask;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Manus 任务的运行时上下文
 * 一个任务一份实例，在执行线程、规划工具与事件推送之间共享：
 * 事件统一经 publish 回调交给 ManusTaskService（先落库再推送 SSE），
 * 交付物经 registerDeliverable 回调由服务层落库并推送，停止标记供执行循环在步骤间检查
 */
public class ManusTaskContext {

    private final String taskId;

    // 事件发布回调（由 ManusTaskService 注入）
    private final Consumer<ManusTask.EventEntry> eventPublisher;

    // 交付物登记回调（由 ManusTaskService 注入）
    private final Consumer<ManusTask.Deliverable> deliverableRegistrar;

    // 停止标记（用户主动停止时置位）
    private final AtomicBoolean stopRequested = new AtomicBoolean(false);

    // 当前最新计划（内存副本，供规划工具读取与防重复创建判断）
    private volatile List<ManusTask.PlanStep> plan = new ArrayList<>();

    public ManusTaskContext(String taskId, Consumer<ManusTask.EventEntry> eventPublisher,
                            Consumer<ManusTask.Deliverable> deliverableRegistrar) {
        this.taskId = taskId;
        this.eventPublisher = eventPublisher;
        this.deliverableRegistrar = deliverableRegistrar;
    }

    public String getTaskId() {
        return taskId;
    }

    /**
     * 发布事件（交给服务层落库并推送 SSE）
     *
     * @param entry 事件条目
     */
    public void publish(ManusTask.EventEntry entry) {
        eventPublisher.accept(entry);
    }

    /**
     * 登记交付物（交给服务层落库并推送 SSE，调用前需完成路径校验）
     *
     * @param deliverable 交付物条目
     */
    public void registerDeliverable(ManusTask.Deliverable deliverable) {
        deliverableRegistrar.accept(deliverable);
    }

    /**
     * 请求停止任务（在当前步骤执行完后生效）
     */
    public void requestStop() {
        stopRequested.set(true);
    }

    /**
     * 是否已请求停止
     *
     * @return 已请求返回 true
     */
    public boolean isStopRequested() {
        return stopRequested.get();
    }

    public List<ManusTask.PlanStep> getPlan() {
        return plan;
    }

    public void setPlan(List<ManusTask.PlanStep> plan) {
        this.plan = plan;
    }
}
