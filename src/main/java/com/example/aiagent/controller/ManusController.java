package com.example.aiagent.controller;

import com.example.aiagent.model.ManusTask;
import com.example.aiagent.model.ManusTaskListItem;
import com.example.aiagent.service.ManusTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

/**
 * Manus 超级智能体专用接口（ReAct 任务制）
 * 与旧接口 /ai/manus/chat 完全独立：旧接口服务于小程序的会话式调用，保持原样不动；
 * 本控制器提供任务制的创建、事件订阅、详情回放、列表与停止能力
 */
@Slf4j
@RestController
@RequestMapping("/ai/manus")
@RequiredArgsConstructor
public class ManusController {

    private final ManusTaskService manusTaskService;

    /**
     * 创建任务并开始异步执行
     *
     * @param body 请求体，task 字段为任务描述
     * @return 已创建的任务文档（含 ID 与初始状态）
     */
    @PostMapping("/task")
    public ManusTask createTask(@RequestBody Map<String, String> body) {
        String task = body != null ? body.get("task") : null;
        return manusTaskService.createTask(task);
    }

    /**
     * 订阅任务的结构化事件流（SSE）
     * 每条事件为 JSON：type（plan_updated/think/tool_call/tool_result/final/error）+ 时间戳与内容
     * 订阅时先回放已发生的事件，任务已结束时回放后立即关闭
     *
     * @param taskId 任务 ID
     * @return SSE 连接
     */
    @GetMapping(value = "/task/{taskId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribeTask(@PathVariable String taskId) {
        return manusTaskService.subscribe(taskId);
    }

    /**
     * 查询任务详情（含计划、事件日志与最终报告，用于回放）
     *
     * @param taskId 任务 ID
     * @return 任务文档
     */
    @GetMapping("/task/{taskId}")
    public ResponseEntity<ManusTask> getTask(@PathVariable String taskId) {
        ManusTask taskDoc = manusTaskService.getTask(taskId);
        if (taskDoc == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(taskDoc);
    }

    /**
     * 查询任务列表（按更新时间倒序，最多 50 条，含计划进度）
     *
     * @return 任务列表
     */
    @GetMapping("/task/list")
    public List<ManusTaskListItem> listTasks() {
        return manusTaskService.listTasks();
    }

    /**
     * 停止任务
     * 执行中的任务在当前步骤执行完毕后停止；排队中未开始的任务直接标记停止
     *
     * @param taskId 任务 ID
     * @return 结果说明
     */
    @PostMapping("/task/{taskId}/stop")
    public Map<String, String> stopTask(@PathVariable String taskId) {
        String message = manusTaskService.stopTask(taskId);
        return Map.of("message", message);
    }
}
