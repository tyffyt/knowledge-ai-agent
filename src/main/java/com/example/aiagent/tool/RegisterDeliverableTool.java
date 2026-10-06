package com.example.aiagent.tool;

import cn.hutool.core.util.StrUtil;
import com.example.aiagent.agent.ManusTaskContext;
import com.example.aiagent.model.ManusTask;
import org.springframework.ai.tool.annotation.Tool;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * 交付物登记工具（Manus 超级智能体专用，每任务一份实例）
 * 模型生成文件后调用本工具登记产出，服务端据此提供预览与下载
 * 安全校验：路径 normalize 后必须位于 {user.dir}/tmp/ 白名单根目录内，且文件真实存在
 */
public class RegisterDeliverableTool {

    /**
     * 白名单根目录：所有产出工具的实际写出目录（tmp/file、tmp/pdf、tmp/download）均在其下
     */
    private static final String WHITELIST_ROOT = Paths.get(System.getProperty("user.dir"), "tmp")
            .normalize().toString();

    /**
     * 交付物大小上限（字节，50MB）
     */
    private static final long MAX_SIZE = 50L * 1024 * 1024;

    /**
     * 可预览的文件扩展名（html/svg 可含脚本，一律不预览，只能下载）
     */
    private static final Set<String> TEXT_EXTENSIONS = Set.of("md", "txt", "log", "json", "csv");
    private static final Set<String> IMAGE_EXTENSIONS = Set.of("png", "jpg", "jpeg", "gif", "webp", "bmp");
    private static final Set<String> PDF_EXTENSIONS = Set.of("pdf");

    private final ManusTaskContext context;

    public RegisterDeliverableTool(ManusTaskContext context) {
        this.context = context;
    }

    /**
     * 登记任务产出的文件为交付物，登记成功后用户可预览与下载
     *
     * @param fileName    展示名称（含扩展名，如 report.pdf）
     * @param filePath    文件路径（绝对路径或相对 tmp 目录的路径）
     * @param description 文件内容说明
     * @return 执行结果说明
     */
    @Tool(description = """
            Register a file generated during this task (PDF, downloaded resource, written file) as a deliverable. \
            Call this tool IMMEDIATELY after a file is successfully created or downloaded, so the user can preview and download it. \
            filePath is the file's absolute path, tmp-relative path, or the download URL returned by the tool that produced it. \
            description briefly explains what the file contains.""")
    public String register(String fileName, String filePath, String description) {
        if (StrUtil.isBlank(fileName) || StrUtil.isBlank(filePath)) {
            return "错误：文件名与文件路径不能为空";
        }
        Path root = Paths.get(WHITELIST_ROOT);
        Path path = resolveWithinWhitelist(filePath.trim());
        // Path 组件级白名单比较（防 tmp2 等同级目录与 ../ 穿越）
        if (path == null || !path.startsWith(root) || path.equals(root)) {
            return "错误：文件路径超出允许的 tmp 目录范围，无法登记为交付物";
        }
        File file = path.toFile();
        if (!file.isFile()) {
            return "错误：文件不存在：" + path.getFileName() + "，请提供生成工具返回的完整路径或文件名";
        }
        if (file.length() > MAX_SIZE) {
            return "错误：文件超过 " + (MAX_SIZE / 1024 / 1024) + "MB 上限，无法登记";
        }
        String extension = StrUtil.subAfter(file.getName(), '.', true).toLowerCase();
        String relativePath = root.relativize(path.normalize()).toString();
        ManusTask.Deliverable deliverable = new ManusTask.Deliverable(
                fileName.trim(),
                relativePath,
                resolveType(extension),
                file.length(),
                StrUtil.isBlank(description) ? "" : description.trim(),
                Instant.now().toString());
        context.registerDeliverable(deliverable);
        return "交付物已登记：" + fileName.trim() + "，用户可预览与下载";
    }

    /**
     * 解析待登记文件路径（不放宽白名单边界）
     * 绝对路径直接使用；相对路径先按白名单根目录解析，未命中时依次在已知产出子目录
     * （pdf / download / file）下精确匹配同名文件——产出工具通常只向模型返回文件名；
     * 仍未命中时按文件名（取路径最后一段，兼容模型拼贴的 /api/files/pdf/x.pdf 下载 URL）
     * 在根目录与各产出子目录下兜底精确匹配
     *
     * @param filePath 模型提供的路径、文件名或下载 URL
     * @return 命中的文件路径；未命中返回根目录解析结果（由调用方做存在性判断）
     */
    private Path resolveWithinWhitelist(String filePath) {
        Path p = Paths.get(filePath).normalize();
        if (p.isAbsolute()) {
            return p;
        }
        Path direct = Paths.get(WHITELIST_ROOT, filePath).normalize();
        if (Files.isRegularFile(direct)) {
            return direct;
        }
        for (String sub : List.of("pdf", "download", "file")) {
            Path candidate = Paths.get(WHITELIST_ROOT, sub, filePath).normalize();
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        // 按文件名兜底：模型可能直接拼贴生成工具返回的下载 URL（如 /api/files/pdf/x.pdf）
        String base = p.getFileName() == null ? "" : p.getFileName().toString();
        if (StrUtil.isNotBlank(base)) {
            Path rootCandidate = Paths.get(WHITELIST_ROOT, base).normalize();
            if (Files.isRegularFile(rootCandidate)) {
                return rootCandidate;
            }
            for (String sub : List.of("pdf", "download", "file")) {
                Path candidate = Paths.get(WHITELIST_ROOT, sub, base).normalize();
                if (Files.isRegularFile(candidate)) {
                    return candidate;
                }
            }
        }
        return direct;
    }

    /**
     * 按扩展名判定文件类型（决定能否预览）
     *
     * @param extension 小写扩展名
     * @return pdf / image / text / binary
     */
    private String resolveType(String extension) {
        if (PDF_EXTENSIONS.contains(extension)) {
            return "pdf";
        }
        if (IMAGE_EXTENSIONS.contains(extension)) {
            return "image";
        }
        if (TEXT_EXTENSIONS.contains(extension)) {
            return "text";
        }
        return "binary";
    }
}
