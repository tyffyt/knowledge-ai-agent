package com.example.aiagent.controller;

import com.example.aiagent.agent.Manus;
import com.example.aiagent.app.KnowledgeApp;
import com.example.aiagent.service.YuqueDocumentSyncService;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import jakarta.servlet.http.HttpServletRequest;
import com.example.aiagent.model.ChatMessages;
import com.example.aiagent.model.ChatHistoryDTO;
import com.example.aiagent.service.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Comparator;
import java.util.Map;

@RestController
@RequestMapping("/ai")
public class AiController {

    @Resource
    private KnowledgeApp knowledgeApp;

    @Resource
    private MongoTemplate mongoTemplate;

    @Resource
    private AuthService authService;

    @Resource
    private YuqueDocumentSyncService yuqueDocumentSyncService;

    private String getUsernameFromRequest(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authService.parseUsername(authHeader.substring(7));
        }
        return null;
    }

    /**
     * 获取会话历史列表
     * @param request
     * @return
     */
    @GetMapping("/knowledge/chat/history")
    public ResponseEntity<List<ChatHistoryDTO>> getKnowledgeAppHistory(HttpServletRequest request) {
        String username = getUsernameFromRequest(request);
        if (username == null) {
            return ResponseEntity.status(401).build();
        }

        // Query chats matching know_{username}_
        Query query = new Query(Criteria.where("conversationId").regex("^know_" + username + "_"));
        List<ChatMessages> chats = mongoTemplate.find(query, ChatMessages.class, "chat_memory");

        // 按修改时间倒序，最新的排在前面；旧数据无修改时间时回退到创建时间且排在最后
        chats.sort(Comparator.comparing(
                (ChatMessages c) -> c.getUpdatedAt() != null ? c.getUpdatedAt() : c.getCreatedAt(),
                Comparator.reverseOrder()));

        List<ChatHistoryDTO> dtoList = chats.stream().map(chat -> {
            String title = chat.getTitle();
            if (title == null || title.isBlank()) {
                title = "未命名会话";
                if (chat.getMessages() != null && !chat.getMessages().isEmpty()) {
                    for (ChatMessages.MessageDocument msg : chat.getMessages()) {
                        if ("user".equalsIgnoreCase(msg.getRole())) {
                            String text = msg.getContent();
                            title = text.length() > 20 ? text.substring(0, 20) + "..." : text;
                            break;
                        }
                    }
                }
            }
            return new ChatHistoryDTO(chat.getId(), chat.getConversationId(), title, chat.getCreatedAt(), chat.getUpdatedAt());
        }).collect(Collectors.toList());

        return ResponseEntity.ok(dtoList);
    }

    /**
     * 获取会话历史详情
     */
    @GetMapping("/knowledge/chat/history/{chatId}")
    public ResponseEntity<List<ChatMessages.MessageDocument>> getKnowledgeAppHistoryDetail(@PathVariable String chatId,
            HttpServletRequest request) {
        String username = getUsernameFromRequest(request);
        if (username == null || !chatId.startsWith("know_" + username + "_")) {
            return ResponseEntity.status(401).build(); // Basic security to ensure users only access their own chats
        }

        Query query = new Query(Criteria.where("conversationId").is(chatId));
        ChatMessages chat = mongoTemplate.findOne(query, ChatMessages.class, "chat_memory");

        if (chat == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(chat.getMessages() != null ? chat.getMessages() : new ArrayList<>());
    }

    /**
     * 更新会话标题
     */
    @PutMapping("/knowledge/chat/history/{chatId}/title")
    public ResponseEntity<Map<String, String>> updateChatTitle(@PathVariable String chatId,
            @RequestBody Map<String, String> body, HttpServletRequest request) {
        String username = getUsernameFromRequest(request);
        if (username == null || !chatId.startsWith("know_" + username + "_")) {
            return ResponseEntity.status(401).build();
        }
        String newTitle = body != null ? body.get("title") : null;
        if (newTitle == null || newTitle.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "标题不能为空"));
        }
        Query query = new Query(Criteria.where("conversationId").is(chatId));
        ChatMessages chat = mongoTemplate.findOne(query, ChatMessages.class, "chat_memory");
        if (chat == null) {
            return ResponseEntity.notFound().build();
        }
        chat.setTitle(newTitle);
        mongoTemplate.save(chat, "chat_memory");
        return ResponseEntity.ok(Map.of("message", "标题更新成功"));
    }

    /**
     * 删除会话
     */
    @DeleteMapping("/knowledge/chat/history/{chatId}")
    public ResponseEntity<Map<String, String>> deleteChat(@PathVariable String chatId,
            HttpServletRequest request) {
        String username = getUsernameFromRequest(request);
        if (username == null || !chatId.startsWith("know_" + username + "_")) {
            return ResponseEntity.status(401).build();
        }
        Query query = new Query(Criteria.where("conversationId").is(chatId));
        ChatMessages chat = mongoTemplate.findOne(query, ChatMessages.class, "chat_memory");
        if (chat == null) {
            return ResponseEntity.notFound().build();
        }
        mongoTemplate.remove(chat, "chat_memory");
        return ResponseEntity.ok(Map.of("message", "会话已删除"));
    }

    /**
     * 批量删除会话
     */
    @PostMapping("/knowledge/chat/history/batch-delete")
    public ResponseEntity<Map<String, Object>> batchDeleteChats(@RequestBody Map<String, List<String>> body,
            HttpServletRequest request) {
        String username = getUsernameFromRequest(request);
        if (username == null) {
            return ResponseEntity.status(401).build();
        }
        List<String> chatIds = body != null ? body.get("chatIds") : null;
        if (chatIds == null || chatIds.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "chatIds 不能为空"));
        }
        // 仅允许删除当前用户自己的会话
        boolean allOwned = chatIds.stream()
                .allMatch(id -> id != null && id.startsWith("know_" + username + "_"));
        if (!allOwned) {
            return ResponseEntity.status(403).build();
        }
        Query query = new Query(Criteria.where("conversationId").in(chatIds));
        long deleted = mongoTemplate.remove(query, ChatMessages.class, "chat_memory").getDeletedCount();
        return ResponseEntity.ok(Map.of("message", "已删除 " + deleted + " 个会话", "deleted", deleted));
    }

    /**
     * 同步调用知识助手应用
     *
     * @param model 模型标识，不传时使用默认模型（兼容 App / 小程序等旧调用方）
     */
    @GetMapping("/knowledge/chat/sync")
    public String doChatWithKnowledgeSync(String message, String chatId, String model) {
        return knowledgeApp.doChat(message, chatId, model);
    }

    /**
     * SSE 流式调用知识助手应用
     */
    @GetMapping(value = "/knowledge/chat/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> doChatWithKnowledgeSSE(String message, String chatId) {
        return knowledgeApp.doChatByStream(message, chatId);
    }

    /**
     * ============== 当前使用这个接口调用（无RAG） ==============
     * 纯文本流式聊天（无 SSE 包装，兼容换行符）
     *
     * @param model 模型标识，不传时使用默认模型（兼容 App / 小程序等旧调用方）
     */
    @GetMapping(value = "/knowledge/chat/stream", produces = MediaType.TEXT_PLAIN_VALUE)
    public Flux<String> doChatWithKnowledgeStream(String message, String chatId, String model) {
        return knowledgeApp.doChatByStream(message, chatId, model);
    }

    /**
     * ============== 当前使用这个接口调用（RAG） ==============
     * 带引用标注的 RAG 流式聊天
     * AI 回复中使用 [1]、[2] 标注引用来源
     * 流结束后追加JSON 格式的引用切片信息
     *
     * @param model 模型标识，不传时使用默认模型（兼容 App / 小程序等旧调用方）
     */
    @GetMapping(value = "/knowledge/chat/rag/stream", produces = MediaType.TEXT_PLAIN_VALUE)
    public Flux<String> doChatWithKnowledgeRagStream(String message, String chatId, String model) {
        return knowledgeApp.doChatByStreamWithRag(message, chatId, model);
    }

    /**
     * 触发语雀文档同步（暂不使用）
     * 从语雀知识库拉取文档并写入 document/yuque-sync/ 目录
     * @return 同步结果
     */
    @PostMapping("/knowledge/yuque/sync")
    public ResponseEntity<Map<String, Object>> syncYuque() {
        int count = yuqueDocumentSyncService.syncDocuments();
        return ResponseEntity.ok(Map.of("success", true, "syncedCount", count));
    }

    /**
     * SSE（emitter） 流式调用知识助手应用
     */
    @GetMapping("/knowledge/chat/sse/emitter")
    public SseEmitter doChatWithKnowledgeEmitter(String message, String chatId) {
        SseEmitter sseEmitter = new SseEmitter(180000L);
        knowledgeApp.doChatByStream(message, chatId).subscribe(chunk -> {
            try {
                sseEmitter.send(chunk);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }, sseEmitter::completeWithError, sseEmitter::complete);
        return sseEmitter;
    }


    @Resource
    private ToolCallback[] allTools;

    @Resource
    private ChatModel dashscopeChatModel;

    /**
     * openManus接口
     * @param message
     * @return
     */
    @GetMapping("/manus/chat")
    public SseEmitter doChatWithManus(String message) {
        return new Manus(allTools, dashscopeChatModel).runStream(message);
    }
}
