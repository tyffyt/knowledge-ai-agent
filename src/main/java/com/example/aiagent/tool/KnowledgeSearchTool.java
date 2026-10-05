package com.example.aiagent.tool;

import cn.hutool.core.util.StrUtil;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;

/**
 * 知识库检索工具（Manus 超级智能体专用）
 * 让智能体能检索用户本地知识库（MongoDB 向量库），检索参数与问答端一致（相似度阈值 0.5、topK 3）
 * 该工具按任务注入，不进入共享 allTools 池，避免问答端与旧 Manus 行为变化
 */
public class KnowledgeSearchTool {

    /**
     * 相似度阈值（与问答端一致，0.6 会漏召回）
     */
    private static final double SIMILARITY_THRESHOLD = 0.5d;

    /**
     * 返回切片数量上限
     */
    private static final int TOP_K = 3;

    /**
     * 单个切片在工具返回中的最大保留长度
     */
    private static final int MAX_CHUNK_LENGTH = 1500;

    private final VectorStore knowledgeVectorStore;

    public KnowledgeSearchTool(VectorStore knowledgeVectorStore) {
        this.knowledgeVectorStore = knowledgeVectorStore;
    }

    /**
     * 检索用户本地知识库，返回最相关的知识切片与来源标题
     *
     * @param query 检索关键词或问题
     * @return 知识切片文本（含来源标注），未命中或知识库为空时返回明确提示
     */
    @Tool(description = """
            Search the user's LOCAL knowledge base (their personal notes and documents). \
            Use this tool when the task involves knowledge management, Java, Spring, AI agent development \
            or other topics likely covered by the user's own notes. \
            Do NOT use it for real-time information (news, prices, weather) — use web search instead. \
            Returns the most relevant note fragments with their source titles.""")
    public String knowledgeSearch(String query) {
        if (StrUtil.isBlank(query)) {
            return "错误：检索关键词不能为空";
        }
        try {
            List<Document> documents = knowledgeVectorStore.similaritySearch(
                    SearchRequest.builder()
                            .query(query)
                            .topK(TOP_K)
                            .similarityThreshold(SIMILARITY_THRESHOLD)
                            .build());
            if (documents == null || documents.isEmpty()) {
                return "知识库中未检索到与「" + query + "」相关的内容，可尝试换一个关键词，或改用网络搜索";
            }
            StringBuilder result = new StringBuilder();
            for (int i = 0; i < documents.size(); i++) {
                Document doc = documents.get(i);
                // 知识库切片的 metadata 实际键为 filename（如 title 不存在时回退），已核实向量库存储结构
                Object title = doc.getMetadata().get("title");
                if (title == null) {
                    title = doc.getMetadata().get("filename");
                }
                String source = title != null ? title.toString() : "未命名笔记";
                String content = doc.getText();
                if (content != null && content.length() > MAX_CHUNK_LENGTH) {
                    content = content.substring(0, MAX_CHUNK_LENGTH) + "…（已截断）";
                }
                result.append("【片段 ").append(i + 1).append(" · 来源：").append(source).append("】\n")
                        .append(StrUtil.nullToEmpty(content)).append("\n\n");
            }
            return result.toString().trim();
        } catch (Exception e) {
            return "知识库检索失败：" + e.getMessage();
        }
    }
}
