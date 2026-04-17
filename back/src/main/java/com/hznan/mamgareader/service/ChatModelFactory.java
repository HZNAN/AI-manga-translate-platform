package com.hznan.mamgareader.service;

import com.hznan.mamgareader.config.AppProperties;
import com.hznan.mamgareader.model.entity.LlmConfig;
import com.hznan.mamgareader.model.enums.LlmProvider;
import com.hznan.mamgareader.model.enums.LlmProvider.ModelType;
import com.google.genai.Client;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.deepseek.api.DeepSeekApi;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.zhipuai.ZhiPuAiChatModel;
import org.springframework.ai.zhipuai.ZhiPuAiChatOptions;
import org.springframework.ai.zhipuai.api.ZhiPuAiApi;
import org.springframework.stereotype.Service;

/**
 * ChatModel 工厂 — 根据 {@link LlmProvider} 枚举类型分派到不同的 Spring AI 原生实现。
 * <p>
 * <b>设计要点：</b>
 * <ul>
 *   <li>工厂模式：根据枚举的 {@link ModelType} 决定创建哪种原生 ChatModel，
 *       对外统一返回 {@link ChatModel} 接口。</li>
 *   <li>枚举驱动：所有 provider 元数据集中在 {@link LlmProvider} 中，
 *       工厂只做 switch 分发，新增 provider 只需加枚举值和一个 case 分支。</li>
 *   <li>原生优先：OpenAI、DeepSeek、Gemini、Ollama、智谱均使用 Spring AI 原生 ChatModel，
 *       仅 SiliconFlow / 自定义端点走 OpenAI 兼容协议。</li>
 *   <li>多模态统一接口：无论底层实现，返回的 {@link ChatModel} 都可直接
 *       接受 {@code UserMessage} 携带的 {@code Media}（图片），调用方无需感知差异。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatModelFactory {

    private final AppProperties appProperties;

    /**
     * 根据用户配置创建对应厂商的 ChatModel 实例。
     *
     * @param config 数据库中的 LLM 配置（含 provider、apiKey、modelName、baseUrl）
     * @return Spring AI 统一的 ChatModel 接口实例
     */
    public ChatModel createChatModel(LlmConfig config) {
        LlmProvider provider = LlmProvider.fromCode(config.getProvider());

        if (!provider.isChatModel()) {
            throw new IllegalArgumentException(provider.getCode() + " 不是对话模型，不能创建 ChatModel");
        }

        log.info("创建 ChatModel: provider={}, modelType={}, model={}",
                provider.getCode(), provider.getModelType(), config.getModelName());

        return switch (provider.getModelType()) {
            case OPENAI -> createOpenAi(config);
            case OLLAMA -> createOllama(config);
            case ZHIPU -> createZhiPu(config);
            case DEEPSEEK -> createDeepSeek(config);
            case GOOGLE_GENAI -> createGoogleGenAi(config);
            case OPENAI_COMPAT -> createOpenAiCompatible(config, provider);
            default -> throw new IllegalStateException("不支持的 ModelType: " + provider.getModelType());
        };
    }

    // ─── OpenAI 原生：内置默认 URL，仅需 API Key ──────────────────────

    private ChatModel createOpenAi(LlmConfig config) {
        String apiKey = requireApiKey(config);

        OpenAiApi api = OpenAiApi.builder()
                .apiKey(apiKey)
                .build();

        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(config.getModelName())
                .build();

        return OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(options)
                .build();
    }

    // ─── Ollama 原生：本地/远程部署，需要用户提供 Base URL ──────────────

    private ChatModel createOllama(LlmConfig config) {
        String baseUrl = config.getBaseUrl() != null && !config.getBaseUrl().isBlank()
                ? config.getBaseUrl()
                : appProperties.getOllama().getBaseUrl();

        OllamaApi api = OllamaApi.builder()
                .baseUrl(baseUrl)
                .build();

        OllamaChatOptions options = OllamaChatOptions.builder()
                .model(config.getModelName())
                .build();

        return OllamaChatModel.builder()
                .ollamaApi(api)
                .defaultOptions(options)
                .build();
    }

    // ─── 智谱 AI 原生：GLM 系列，API 路径为 /v4 ────────────────────────

    private ChatModel createZhiPu(LlmConfig config) {
        String apiKey = requireApiKey(config);

        ZhiPuAiApi api = ZhiPuAiApi.builder()
                .apiKey(apiKey)
                .build();

        ZhiPuAiChatOptions options = ZhiPuAiChatOptions.builder()
                .thinking(ZhiPuAiApi.ChatCompletionRequest.Thinking.disabled())
                .model(config.getModelName())
                .build();

        return new ZhiPuAiChatModel(api, options);
    }

    // ─── DeepSeek 原生：内置默认 URL，仅需 API Key ────────────────────

    private ChatModel createDeepSeek(LlmConfig config) {
        String apiKey = requireApiKey(config);

        DeepSeekApi api = DeepSeekApi.builder()
                .apiKey(apiKey)
                .build();

        DeepSeekChatOptions options = DeepSeekChatOptions.builder()
                .model(config.getModelName())
                .build();

        return DeepSeekChatModel.builder()
                .deepSeekApi(api)
                .defaultOptions(options)
                .build();
    }

    // ─── Google Gemini 原生：通过 Google GenAI SDK，仅需 API Key ──────

    private ChatModel createGoogleGenAi(LlmConfig config) {
        String apiKey = requireApiKey(config);

        Client genAiClient = Client.builder()
                .apiKey(apiKey)
                .build();

        GoogleGenAiChatOptions options = GoogleGenAiChatOptions.builder()
                .model(config.getModelName())
                .build();

        return GoogleGenAiChatModel.builder()
                .genAiClient(genAiClient)
                .defaultOptions(options)
                .build();
    }

    // ─── OpenAI 兼容协议：SiliconFlow / 自定义端点 ─────────────────────

    private ChatModel createOpenAiCompatible(LlmConfig config, LlmProvider provider) {
        String baseUrl = resolveBaseUrl(config, provider);
        String apiKey = requireApiKey(config);

        OpenAiApi api = OpenAiApi.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .build();

        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(config.getModelName())
                .build();

        return OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(options)
                .build();
    }

    // ─── 辅助方法 ──────────────────────────────────────────────────────

    /**
     * 解析 OPENAI_COMPAT 类型的 Base URL：用户自定义 > 枚举默认值。
     */
    private String resolveBaseUrl(LlmConfig config, LlmProvider provider) {
        if (config.getBaseUrl() != null && !config.getBaseUrl().isBlank()) {
            return config.getBaseUrl();
        }
        if (provider.getDefaultBaseUrl() != null) {
            return provider.getDefaultBaseUrl();
        }
        throw new IllegalArgumentException(provider.getCode() + " 未配置 baseUrl 且无默认值");
    }

    private String requireApiKey(LlmConfig config) {
        if (config.getApiKey() == null || config.getApiKey().isBlank()) {
            throw new IllegalArgumentException("API Key 未配置，provider=" + config.getProvider());
        }
        return config.getApiKey();
    }
}
