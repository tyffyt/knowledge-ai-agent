package com.example.aiagent.controller;

import com.example.aiagent.model.ChatModelDTO;
import com.example.aiagent.service.ChatModelService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 大模型清单接口（登录即可访问，权限校验由 AuthFilter 统一处理，无管理员限制）
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/ai/model")
public class ChatModelController {

    private final ChatModelService chatModelService;

    /**
     * 查询可切换的大模型清单
     * GET /api/ai/model/list
     */
    @GetMapping("/list")
    public List<ChatModelDTO> list() {
        return chatModelService.list();
    }
}
