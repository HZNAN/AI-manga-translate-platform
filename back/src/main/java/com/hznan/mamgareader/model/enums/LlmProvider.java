package com.hznan.mamgareader.model.enums;

import lombok.Getter;

/**
 * LLM 服务提供商枚举。
 * <p>
 * 集中管理每个 provider 的默认 Base URL、创建类型以及是否支持多模态能力等元数据，
 * 消除散落在各处的字符串魔法值。
 * </p>
 *
 * <h3>创建类型说明</h3>
 * <ul>
 *   <li>{@link ModelType#OPENAI} — OpenAI 官方，使用 Spring AI 原生 {@code OpenAiChatModel}，
 *       内置默认 API 地址，仅需 API Key。</li>
 *   <li>{@link ModelType#OLLAMA} — 本地 / 远程 Ollama 服务，使用 {@code OllamaChatModel}，
 *       需要用户提供 Base URL。</li>
 *   <li>{@link ModelType#ZHIPU} — 智谱 AI（GLM 系列），使用 {@code ZhiPuAiChatModel}，
 *       其 API 路径后缀为 /v4，无法直接复用 OpenAI /v1 协议。</li>
 *   <li>{@link ModelType#DEEPSEEK} — DeepSeek，使用 Spring AI 原生 {@code DeepSeekChatModel}。</li>
 *   <li>{@link ModelType#GOOGLE_GENAI} — Google Gemini，使用 {@code GoogleGenAiChatModel}，
 *       通过 Google GenAI Java SDK（{@code com.google.genai.Client}）连接。</li>
 *   <li>{@link ModelType#OPENAI_COMPAT} — 兼容 OpenAI /v1 协议的第三方厂商（SiliconFlow 等）
 *       或用户自定义端点，统一使用 {@code OpenAiChatModel} + 自定义 Base URL。</li>
 *   <li>{@link ModelType#TENCENT_MT} — 腾讯云机器翻译，非 LLM，走独立 SDK 调用。</li>
 * </ul>
 */
@Getter
public enum LlmProvider {

    OLLAMA("ollama", ModelType.OLLAMA, null, true),
    OPENAI("openai", ModelType.OPENAI, null, true),
    DEEPSEEK("deepseek", ModelType.DEEPSEEK, null, false),
    ZHIPU("zhipu", ModelType.ZHIPU, null, true),
    /** SiliconFlow 走 OpenAI 兼容协议，默认 Base URL 由枚举提供，前端不需要用户填写 */
    SILICONFLOW("siliconflow", ModelType.OPENAI_COMPAT, "https://api.siliconflow.cn/v1", true),
    GEMINI("gemini", ModelType.GOOGLE_GENAI, null, true),
    /** 自定义 OpenAI 兼容服务，无默认 Base URL，用户必须自行提供 */
    CUSTOM("custom", ModelType.OPENAI_COMPAT, null, true),
    TENCENT("tencent", ModelType.TENCENT_MT, null, false);

    /** 存储在数据库 llm_configs.provider 列中的值 */
    private final String code;

    /** 决定使用哪种 Spring AI ChatModel 实现 */
    private final ModelType modelType;

    /** 该厂商的默认 API Base URL；若为 null 则由 SDK 内置或由用户提供 */
    private final String defaultBaseUrl;

    /** 该厂商是否支持多模态（图片+文本）输入 */
    private final boolean multimodalCapable;

    LlmProvider(String code, ModelType modelType, String defaultBaseUrl, boolean multimodalCapable) {
        this.code = code;
        this.modelType = modelType;
        this.defaultBaseUrl = defaultBaseUrl;
        this.multimodalCapable = multimodalCapable;
    }

    /**
     * 根据数据库中存储的 provider 字符串反查枚举实例。
     *
     * @param code 数据库中的 provider 值，如 "ollama"、"zhipu"
     * @return 对应的枚举实例
     * @throws IllegalArgumentException 未知的 provider
     */
    public static LlmProvider fromCode(String code) {
        for (LlmProvider p : values()) {
            if (p.code.equalsIgnoreCase(code)) {
                return p;
            }
        }
        throw new IllegalArgumentException("未知的 LLM Provider: " + code);
    }

    /** 是否属于 LLM 对话模型（排除腾讯机器翻译） */
    public boolean isChatModel() {
        return modelType != ModelType.TENCENT_MT;
    }

    /**
     * ChatModel 创建类型，决定工厂使用哪种 Spring AI 实现类来构建模型实例。
     */
    public enum ModelType {
        /** OpenAI 官方 → {@code OpenAiChatModel}（内置默认 URL，仅需 API Key） */
        OPENAI,
        /** Ollama 本地/远程 → {@code OllamaChatModel}（需要用户提供 Base URL） */
        OLLAMA,
        /** 智谱 AI /v4 → {@code ZhiPuAiChatModel} */
        ZHIPU,
        /** DeepSeek → {@code DeepSeekChatModel}（内置默认 URL，仅需 API Key） */
        DEEPSEEK,
        /** Google Gemini → {@code GoogleGenAiChatModel}（通过 Google GenAI SDK，仅需 API Key） */
        GOOGLE_GENAI,
        /** 兼容 OpenAI /v1 协议 → {@code OpenAiChatModel} + 自定义 Base URL */
        OPENAI_COMPAT,
        /** 腾讯云机器翻译 → 独立 SDK，非 ChatModel */
        TENCENT_MT
    }
}
