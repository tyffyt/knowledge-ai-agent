package com.example.aiagent.controller;

import com.example.aiagent.model.ManusTask;
import com.example.aiagent.model.ManusTaskListItem;
import com.example.aiagent.service.ManusTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Manus 超级智能体专用接口（ReAct 任务制）
 * 与旧接口 /ai/manus/chat 完全独立：旧接口服务于小程序的会话式调用，保持原样不动；
 * 本控制器提供任务制的创建、事件订阅、详情回放、列表、停止、多轮追问与交付物预览下载能力
 */
@Slf4j
@RestController
@RequestMapping("/ai/manus")
@RequiredArgsConstructor
public class ManusController {

    /**
     * 允许预览的文件类型（binary 及 html/svg 等只允许下载）
     */
    private static final Set<String> PREVIEWABLE_TYPES = Set.of("pdf", "image", "text");

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
    public SseEmitter subscribeTask(@PathVariable String taskId,
                                    @RequestParam(defaultValue = "0") int after) {
        return manusTaskService.subscribe(taskId, after);
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

    /**
     * 对已结束的任务追加用户追问，任务回到 RUNNING 并重新执行一轮
     * 新事件追加到同一任务文档，回放完整
     *
     * @param taskId 任务 ID
     * @param body   请求体，message 字段为追问内容
     * @return 更新后的任务文档
     */
    @PostMapping("/task/{taskId}/message")
    public ManusTask followUp(@PathVariable String taskId, @RequestBody Map<String, String> body) {
        String message = body != null ? body.get("message") : null;
        return manusTaskService.addFollowUp(taskId, message);
    }

    /**
     * 查询任务交付物清单
     *
     * @param taskId 任务 ID
     * @return 交付物列表
     */
    @GetMapping("/task/{taskId}/deliverables")
    public List<ManusTask.Deliverable> listDeliverables(@PathVariable String taskId) {
        ManusTask taskDoc = manusTaskService.getTask(taskId);
        if (taskDoc == null) {
            throw new IllegalArgumentException("任务不存在：" + taskId);
        }
        return taskDoc.getDeliverables() == null ? List.of() : taskDoc.getDeliverables();
    }

    /**
     * 下载交付物文件（attachment）
     *
     * @param taskId 任务 ID
     * @param index  交付物序号（1 开始）
     * @return 文件流
     */
    @GetMapping("/task/{taskId}/deliverable/{index}/download")
    public ResponseEntity<Resource> downloadDeliverable(@PathVariable String taskId, @PathVariable int index) {
        ManusTaskService.DeliverableFile file = manusTaskService.resolveDeliverableFile(taskId, index);
        if (file == null) {
            return notFound("交付物不存在或文件已被清理");
        }
        return fileResponse(file, "attachment");
    }

    /**
     * 预览交付物文件（inline；仅文本/图片/PDF 放行）
     *
     * @param taskId 任务 ID
     * @param index  交付物序号（1 开始）
     * @return 文件流
     */
    @GetMapping("/task/{taskId}/deliverable/{index}/preview")
    public ResponseEntity<Resource> previewDeliverable(@PathVariable String taskId, @PathVariable int index) {
        ManusTaskService.DeliverableFile file = manusTaskService.resolveDeliverableFile(taskId, index);
        if (file == null || file.meta().getType() == null
                || !PREVIEWABLE_TYPES.contains(file.meta().getType())) {
            throw new IllegalArgumentException("该文件类型不支持在线预览，请下载后查看");
        }
        return fileResponse(file, "inline");
    }

    /**
     * 构建文件响应（Content-Disposition 指定 inline/attachment，文件名 UTF-8 编码，禁用 MIME 嗅探）
     *
     * @param file       交付物文件
     * @param disposition inline 或 attachment
     * @return 文件流响应
     */
    private ResponseEntity<Resource> fileResponse(ManusTaskService.DeliverableFile file, String disposition) {
        String encodedName = URLEncoder.encode(file.meta().getName(), StandardCharsets.UTF_8).replace("+", "%20");
        MediaType mediaType = resolveMediaType(file.meta().getType(), file.meta().getName());
        return ResponseEntity.ok()
                .header("Content-Disposition", disposition + "; filename=\"download\"; filename*=UTF-8''" + encodedName)
                .header("X-Content-Type-Options", "nosniff")
                .contentType(mediaType)
                .body(new FileSystemResource(file.path()));
    }

    /**
     * 按交付物类型与扩展名解析响应的媒体类型
     *
     * @param type     交付物类型（pdf/image/text/binary）
     * @param fileName 文件名
     * @return 媒体类型
     */
    private MediaType resolveMediaType(String type, String fileName) {
        String lower = fileName == null ? "" : fileName.toLowerCase();
        if ("pdf".equals(type)) {
            return MediaType.APPLICATION_PDF;
        }
        if ("image".equals(type)) {
            if (lower.endsWith(".png")) return MediaType.IMAGE_PNG;
            if (lower.endsWith(".gif")) return MediaType.IMAGE_GIF;
            if (lower.endsWith(".webp")) return MediaType.parseMediaType("image/webp");
            if (lower.endsWith(".bmp")) return MediaType.parseMediaType("image/bmp");
            return MediaType.IMAGE_JPEG;
        }
        if ("text".equals(type)) {
            return new MediaType("text", "plain", StandardCharsets.UTF_8);
        }
        return MediaType.APPLICATION_OCTET_STREAM;
    }

    /**
     * 404 响应（带中文提示）
     *
     * @param message 提示信息
     * @return 404 响应
     */
    private ResponseEntity<Resource> notFound(String message) {
        log.warn("Manus 交付物请求失败: {}", message);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }
}
