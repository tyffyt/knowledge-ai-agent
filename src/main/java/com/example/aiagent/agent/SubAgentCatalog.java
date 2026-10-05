package com.example.aiagent.agent;

import java.util.List;
import java.util.Set;

/**
 * 子智能体角色目录（agent-as-tool）
 * 集中定义每个子智能体角色的标识、展示名、派发引导描述、系统提示词与工具白名单；
 * 白名单按共享工具的 @Tool 方法名过滤，层级限制靠"白名单不含 delegate"实现
 */
public final class SubAgentCatalog {

    /**
     * 主智能体在事件流中的角色标识
     */
    public static final String MAIN = "main";

    private SubAgentCatalog() {
    }

    /**
     * 子智能体角色定义
     */
    public record SubAgentDefinition(
            String key,
            String displayName,
            String description,
            String systemPrompt,
            Set<String> toolNames) {
    }

    /**
     * 全部子智能体角色（顺序即主智能体派发引导中的展示顺序）
     */
    public static final List<SubAgentDefinition> ALL = List.of(
            new SubAgentDefinition("researcher", "联网研究员",
                    "适合需要深度收集网络资料的研究类子任务（多关键词搜索、网页阅读、资料整理）",
                    """
                            你是联网研究员，负责深度收集网络资料。围绕分配给你的子任务，使用搜索与网页抓取工具从多个角度收集信息，\
                            整理为结构化的调研结果返回。信息不足或搜索失败时如实说明。完成或无法继续时调用 doTerminate 结束。""",
                    Set.of("searchWeb", "searchWebAdvanced", "scrapeWebPageText", "scrapeWebPageSummary", "doTerminate")),
            new SubAgentDefinition("knowledgeResearcher", "知识库研究员",
                    "适合需要从用户本地知识库多角度检索与归纳的子任务",
                    """
                            你是知识库研究员，负责从用户本地知识库检索与归纳。围绕子任务用 knowledgeSearch 多角度检索\
                            （可尝试不同关键词组合），整理成结构化结果返回并标注来源文件。检索不到相关内容时如实说明。\
                            完成或无法继续时调用 doTerminate 结束。""",
                    Set.of("knowledgeSearch", "doTerminate")),
            new SubAgentDefinition("writer", "撰写员",
                    "适合把已有材料整理成文档或 PDF 文件并登记为交付物的子任务",
                    """
                            你是文档撰写员，负责把给定的材料整理成文档。使用 writeFile 写出 Markdown 文档（需要 PDF 时用 generatePDF），\
                            写完后必须用 register 登记为交付物，最后返回文件名与内容概要。完成或无法继续时调用 doTerminate 结束。""",
                    Set.of("writeFile", "readFile", "listFiles", "getFileInfo", "generatePDF", "register", "doTerminate")));

    /**
     * 按标识查找角色定义
     *
     * @param key 角色 key
     * @return 角色定义，不存在返回 null
     */
    public static SubAgentDefinition find(String key) {
        if (key == null) {
            return null;
        }
        for (SubAgentDefinition def : ALL) {
            if (def.key().equals(key)) {
                return def;
            }
        }
        return null;
    }
}
