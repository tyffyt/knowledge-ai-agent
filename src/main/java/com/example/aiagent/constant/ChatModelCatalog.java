package com.example.aiagent.constant;

import java.util.List;

/**
 * 大模型目录（静态配置数据）
 * 每个条目对应 MyChatClientConfig 中的一个 ChatClient Bean，Bean 名称与 key 一致
 * 参数与价格取自各厂商官方文档，出处见 sourceUrl、核对日期见 verifiedAt
 * 官网未给出的项为 null，前端显示为「—」
 */
public final class ChatModelCatalog {

    /** DeepSeek V4.1 Flash */
    public static final String DEEPSEEK_FLASH = "deepseek-flash";

    /** DeepSeek V4 Pro */
    public static final String DEEPSEEK_V4_PRO = "deepseek-v4-pro";

    /** 千问 Qwen3.7 Plus */
    public static final String QWEN_37_PLUS = "qwen3.7-plus";

    /** 千问 Qwen3.8 Flash */
    public static final String QWEN_38_FLASH = "qwen3.8-flash";

    /** 未指定模型时使用的默认模型，与切换功能上线前的行为保持一致 */
    public static final String DEFAULT_KEY = DEEPSEEK_FLASH;

    /** 千问所属厂商，用于判断密钥是否就绪 */
    public static final String PROVIDER_QWEN = "阿里云百炼";

    /** DeepSeek 定价说明（高峰/空闲时段） */
    private static final String DEEPSEEK_PRICE_NOTE =
            "空闲时段价格为高峰时段的一半；高峰时段为北京时间周一至周五 9:00-12:00、14:00-18:00（不含法定节假日）";

    /** 千问定价说明（按输入长度分档） */
    private static final String QWEN_PRICE_NOTE =
            "按单次请求的输入长度分档计价；以上为华北2（北京）地域价格";

    /** 价格单位 */
    private static final String PRICE_UNIT = "元 / 百万 tokens";

    /**
     * 一条价格说明
     * 两个厂商的计价维度不同（DeepSeek 区分高峰/空闲，千问按输入长度分档），故用标签区分而非强行统一
     *
     * @param label 价格项名称，含适用档位
     * @param price 单价，多个档位时按 label 中的顺序对应
     * @param unit  单位
     */
    public record PriceItem(String label, String price, String unit) {
    }

    /**
     * 模型信息
     * 长度类字段保留官网原文写法（如 "1M"、"384K"、"131,072"），不换算成统一数值
     *
     * @param key          模型标识，同时作为 ChatClient Bean 名称、前端传参与厂商 API 的模型名称；
     *                     若将来厂商模型名需要与内部标识分离，需在此新增独立字段，不可只改 key
     * @param displayName  展示名称
     * @param provider     所属厂商
     * @param description  简介
     * @param contextWindow 上下文长度
     * @param maxInput     最大输入长度
     * @param maxOutput    最大输出长度
     * @param maxReasoning 最大思维链长度
     * @param prices       价格明细
     * @param priceNote    价格说明（时段或档位规则）
     * @param capabilities 能力标签
     * @param rateLimit    限流或并发上限
     * @param sourceUrl    数据来源
     * @param verifiedAt   核对日期
     */
    public record ModelInfo(
            String key,
            String displayName,
            String provider,
            String description,
            String contextWindow,
            String maxInput,
            String maxOutput,
            String maxReasoning,
            List<PriceItem> prices,
            String priceNote,
            List<String> capabilities,
            String rateLimit,
            String sourceUrl,
            String verifiedAt
    ) {
    }

    private static final List<ModelInfo> MODELS = List.of(
            new ModelInfo(
                    DEEPSEEK_FLASH,
                    "DeepSeek V4.1 Flash",
                    "DeepSeek",
                    "DeepSeek 主力对话模型，速度快、成本低，支持视觉与思考模式",
                    "1M",
                    null,
                    "384K",
                    null,
                    List.of(
                            new PriceItem("输入（缓存未命中）· 空闲 / 高峰", "1 / 2", PRICE_UNIT),
                            new PriceItem("输入（缓存命中）· 空闲 / 高峰", "0.02 / 0.04", PRICE_UNIT),
                            new PriceItem("输出 · 空闲 / 高峰", "4 / 8", PRICE_UNIT)
                    ),
                    DEEPSEEK_PRICE_NOTE,
                    List.of("工具调用", "JSON 输出", "思考模式", "视觉"),
                    "并发上限 2500",
                    "https://api-docs.deepseek.com/zh-cn/quick_start/pricing",
                    "2026-09-28"
            ),
            new ModelInfo(
                    DEEPSEEK_V4_PRO,
                    "DeepSeek V4 Pro",
                    "DeepSeek",
                    "DeepSeek 旗舰模型，推理与复杂任务能力更强，不支持视觉输入",
                    "1M",
                    null,
                    "384K",
                    null,
                    List.of(
                            new PriceItem("输入（缓存未命中）· 空闲 / 高峰", "4.5 / 9.0", PRICE_UNIT),
                            new PriceItem("输入（缓存命中）· 空闲 / 高峰", "0.15 / 0.30", PRICE_UNIT),
                            new PriceItem("输出 · 空闲 / 高峰", "13.5 / 27.0", PRICE_UNIT)
                    ),
                    DEEPSEEK_PRICE_NOTE,
                    List.of("工具调用", "JSON 输出", "思考模式"),
                    "并发上限 500",
                    "https://api-docs.deepseek.com/zh-cn/quick_start/pricing",
                    "2026-09-28"
            ),
            new ModelInfo(
                    QWEN_37_PLUS,
                    "Qwen3.7 Plus",
                    PROVIDER_QWEN,
                    "千问均衡型多模态模型，兼顾能力与成本，适合通用业务场景",
                    "1,000,000",
                    "991,808（思考模式 983,616）",
                    "131,072",
                    "262,144",
                    List.of(
                            new PriceItem("输入 · 输入 ≤256K", "2", PRICE_UNIT),
                            new PriceItem("输出 · 输入 ≤256K", "8", PRICE_UNIT),
                            new PriceItem("输入 · 输入 256K–1M", "6", PRICE_UNIT),
                            new PriceItem("输出 · 输入 256K–1M", "24", PRICE_UNIT),
                            new PriceItem("输入（缓存命中）· ≤256K / 256K–1M", "0.4 / 1.2", PRICE_UNIT)
                    ),
                    QWEN_PRICE_NOTE,
                    List.of("工具调用", "结构化输出", "思考模式", "多模态输入", "前缀续写", "上下文缓存"),
                    "华北2（北京）30,000 RPM / 5,000,000 TPM",
                    "https://help.aliyun.com/zh/model-studio/qwen3-7-plus",
                    "2026-09-28"
            ),
            new ModelInfo(
                    QWEN_38_FLASH,
                    "Qwen3.8 Flash",
                    PROVIDER_QWEN,
                    "千问高速多模态模型，延迟与成本更优，适合高频对话场景",
                    "1,000,000",
                    "991,808（思考模式 983,616）",
                    "131,072",
                    "262,144",
                    List.of(
                            new PriceItem("输入", "0.8", PRICE_UNIT),
                            new PriceItem("输出", "2.7", PRICE_UNIT),
                            new PriceItem("输入（缓存命中）", "0.1", PRICE_UNIT)
                    ),
                    QWEN_PRICE_NOTE,
                    List.of("工具调用", "结构化输出", "思考模式", "多模态输入", "前缀续写", "上下文缓存"),
                    "华北2（北京）动态限流，按平台月消费额度分档",
                    "https://help.aliyun.com/zh/model-studio/qwen3-8-flash",
                    "2026-09-28"
            )
    );

    /**
     * 获取全部模型信息
     *
     * @return 模型列表，顺序即前端展示顺序
     */
    public static List<ModelInfo> all() {
        return MODELS;
    }

    /**
     * 按模型标识查找模型信息
     *
     * @param key 模型标识
     * @return 匹配的模型信息，未找到返回 null
     */
    public static ModelInfo find(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        for (ModelInfo info : MODELS) {
            if (info.key().equals(key)) {
                return info;
            }
        }
        return null;
    }

    private ChatModelCatalog() {
    }
}
