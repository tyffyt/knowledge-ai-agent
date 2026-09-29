package com.example.aiagent.model;

import com.example.aiagent.constant.ChatModelCatalog;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 大模型清单项（供前端模型下拉框与模型展示页使用）
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ChatModelDTO {

    /** 模型标识，与 ChatClient Bean 名称一致 */
    private String key;

    /** 展示名称 */
    private String displayName;

    /** 所属厂商 */
    private String provider;

    /** 简介 */
    private String description;

    /** 上下文长度（官网原文写法） */
    private String contextWindow;

    /** 最大输入长度，官网未给出为 null */
    private String maxInput;

    /** 最大输出长度，官网未给出为 null */
    private String maxOutput;

    /** 最大思维链长度，官网未给出为 null */
    private String maxReasoning;

    /** 价格明细 */
    private List<ChatModelCatalog.PriceItem> prices;

    /** 价格说明（时段或档位规则） */
    private String priceNote;

    /** 能力标签 */
    private List<String> capabilities;

    /** 限流或并发上限 */
    private String rateLimit;

    /** 数据来源 */
    private String sourceUrl;

    /** 核对日期 */
    private String verifiedAt;

    /** 是否可用：厂商密钥未配置时为 false，前端置灰不可选 */
    private boolean available;

    /** 是否为默认模型 */
    private boolean defaultModel;
}
