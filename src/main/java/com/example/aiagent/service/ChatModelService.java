package com.example.aiagent.service;

import com.example.aiagent.constant.ChatModelCatalog;
import com.example.aiagent.model.ChatModelDTO;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 大模型清单与解析服务：对外提供可切换的模型清单，并把模型标识解析为对应的 ChatClient
 * 只负责清单与解析，不创建 ChatClient（ChatClient 由 config/MyChatClientConfig 按模型提供）
 */
@Service
public class ChatModelService {

    /** ChatClient 集合，key 为模型标识（Spring 按 Bean 名称注入） */
    private final Map<String, ChatClient> chatClients;

    /** 千问（DashScope）API Key，未配置时千问模型不可用 */
    private final String dashscopeApiKey;

    public ChatModelService(Map<String, ChatClient> chatClients,
                            @Value("${spring.ai.dashscope.api-key:}") String dashscopeApiKey) {
        this.chatClients = chatClients;
        this.dashscopeApiKey = dashscopeApiKey;
    }

    /**
     * 查询全部模型清单
     *
     * @return 模型列表，每项含可用性与默认模型标记，顺序即前端展示顺序
     */
    public List<ChatModelDTO> list() {
        List<ChatModelDTO> result = new ArrayList<>();
        for (ChatModelCatalog.ModelInfo info : ChatModelCatalog.all()) {
            result.add(toDTO(info));
        }
        return result;
    }

    /**
     * 按模型标识解析出对应的 ChatClient
     * 标识为空时使用默认模型；标识未知或模型不可用时抛出参数异常，
     * 由 GlobalExceptionHandler 统一返回 400 与中文提示，不向用户暴露堆栈
     *
     * @param key 模型标识，可为空
     * @return 对应的 ChatClient
     */
    public ChatClient resolveChatClient(String key) {
        String modelKey = StringUtils.hasText(key) ? key : ChatModelCatalog.DEFAULT_KEY;
        ChatModelCatalog.ModelInfo info = ChatModelCatalog.find(modelKey);
        if (info == null) {
            throw new IllegalArgumentException("不支持的大模型：" + modelKey);
        }
        if (!isAvailable(info)) {
            throw new IllegalArgumentException("大模型「" + info.displayName() + "」当前不可用：未配置对应厂商的 API Key");
        }
        ChatClient chatClient = chatClients.get(modelKey);
        if (chatClient == null) {
            throw new IllegalArgumentException("大模型「" + info.displayName() + "」未注册，请联系管理员");
        }
        return chatClient;
    }

    /**
     * 判断模型是否可用
     * 千问系列依赖 DashScope API Key，未配置时不可选；其余厂商的密钥为必填项，启动阶段即已校验
     *
     * @param info 模型信息
     * @return 可用返回 true
     */
    private boolean isAvailable(ChatModelCatalog.ModelInfo info) {
        if (ChatModelCatalog.PROVIDER_QWEN.equals(info.provider())) {
            return StringUtils.hasText(dashscopeApiKey);
        }
        return true;
    }

    /**
     * 目录条目转清单项，并补齐可用性与默认模型标记
     *
     * @param info 模型信息
     * @return 前端使用的清单项
     */
    private ChatModelDTO toDTO(ChatModelCatalog.ModelInfo info) {
        ChatModelDTO dto = new ChatModelDTO();
        dto.setKey(info.key());
        dto.setDisplayName(info.displayName());
        dto.setProvider(info.provider());
        dto.setDescription(info.description());
        dto.setContextWindow(info.contextWindow());
        dto.setMaxInput(info.maxInput());
        dto.setMaxOutput(info.maxOutput());
        dto.setMaxReasoning(info.maxReasoning());
        dto.setPrices(info.prices());
        dto.setPriceNote(info.priceNote());
        dto.setCapabilities(info.capabilities());
        dto.setRateLimit(info.rateLimit());
        dto.setSourceUrl(info.sourceUrl());
        dto.setVerifiedAt(info.verifiedAt());
        dto.setAvailable(isAvailable(info));
        dto.setDefaultModel(ChatModelCatalog.DEFAULT_KEY.equals(info.key()));
        return dto;
    }
}
