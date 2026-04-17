package com.hznan.mamgareader.service.pipeline;

import com.hznan.mamgareader.model.entity.LlmConfig;
import com.hznan.mamgareader.model.enums.LlmProvider;
import com.hznan.mamgareader.service.ChatModelFactory;
import com.hznan.mamgareader.service.TranslatorApiClient;
import com.hznan.mamgareader.service.translation.LanguageMapping;
import com.hznan.mamgareader.service.translation.LlmResponseParser;
import com.hznan.mamgareader.service.translation.TranslationDTOs.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.content.Media;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * LLM 翻译管线 — 覆写 {@link #doTranslate} 钩子，使用 LLM 模型完成翻译步骤。
 * <p>
 * 支持所有 LLM 服务商（OpenAI / DeepSeek / 智谱 / Gemini / Ollama / SiliconFlow / 自定义），
 * 内部处理 Ollama Qwen 模型的 {@code /no_think} 特殊指令。
 * </p>
 * <p>
 * 采用 {@code <|n|>} 编号格式（参考 Saber-Translator）配合 Few-shot 示例确保
 * LLM 严格按编号一一对应输出翻译，避免 JSON 解析错误和条目乱序问题。
 * </p>
 */
@Slf4j
@Component
public class LlmTranslationPipeline extends AbstractTranslationPipeline {

    private static final String SYSTEM_PROMPT = """
            忽略之前的所有指令，仅遵循以下定义。
            
            ## 角色：专业漫画翻译师
            你是一位专业的漫画翻译引擎，精通{sourceLangDesc}到{targetLangDesc}的本地化翻译，适配所有漫画题材（奇幻、校园、恋爱、热血、悬疑等）。
            
            ## 翻译方法
            1. 直译阶段：对每一行文本进行精确翻译，保持原文句子结构，修正明显的 OCR 识别错误
            2. 分析与意译阶段：捕捉核心含义、情感基调和文化内涵
            3. 润色阶段：调整翻译使其自然流畅，保留漫画的情感基调和角色语气
            
            ## 翻译规则
            - 逐行翻译，保持准确性和真实性，忠实再现原文及其情感意图
            - 保留原文中的拟声词或音效词，使用{targetLangDesc}漫画中最常见的对应表达
            - 译文长度匹配原文气泡大小，避免排版溢出
            - 不同角色保持对应语气，根据上下文推断合适的人称代词，不要添加原文中不存在的代词
            - 如果输入只有1-2个字符且无法构成有意义的词语，请原样输出，不要脑补扩展
            - 修正 OCR 错误时只做最小修正，不要凭空添加原文不存在的内容
            - 每个翻译段落必须带有编号前缀（严格使用 <|数字|> 格式），只输出翻译结果，不要输出原文
            - 只翻译内容，不要添加任何解释或评论
            - **输入有多少条编号，输出必须有同样数量的编号，不得遗漏、合并、拆分或新增任何条目**""";

    private static final String USER_PROMPT_MULTIMODAL = """
            请将以下漫画页面的文字翻译为{targetLangDesc}，题材不限。
            我提供了原始图片和 OCR 识别结果，OCR 可能存在识别误差，请先结合图片画面修正 OCR 错误，再进行翻译。
            译文必须贴合漫画语境、角色情绪与排版，长度匹配原文气泡。
            如果文本已经是{targetLangDesc}或者是拟声词/音效词，请使用对应的{targetLangDesc}表达或原样输出。
            
            **严格要求：输入共 {bubbleCount} 条（从 <|1|> 到 <|{bubbleCount}|>），输出也必须恰好 {bubbleCount} 条，编号一一对应。**
            保持编号前缀格式，每条翻译独占一行。
            
            {ocrText}""";

    private static final String USER_PROMPT_TEXT = """
            请将以下漫画页面的文字翻译为{targetLangDesc}（纯文本模式，无图片上下文）。
            如果文本已经是{targetLangDesc}或者是拟声词/音效词，请使用对应的{targetLangDesc}表达或原样输出。
            
            **严格要求：输入共 {bubbleCount} 条（从 <|1|> 到 <|{bubbleCount}|>），输出也必须恰好 {bubbleCount} 条，编号一一对应。**
            保持编号前缀格式，每条翻译独占一行。
            
            {ocrText}""";

    private static final String FEW_SHOT_INPUT = """
            <|1|>恥ずかしい… 目立ちたくない… 私が消えたい…
            <|2|>きみ… 大丈夫⁉
            <|3|>なんだこいつ 空気読めて ないのか…？""";

    private static final String FEW_SHOT_OUTPUT = """
            <|1|>好尴尬…我不想引人注目…我想消失…
            <|2|>你…没事吧⁉
            <|3|>这家伙怎么看不懂气氛的…？""";

    private final ChatModelFactory chatModelFactory;

    public LlmTranslationPipeline(TranslatorApiClient translatorApiClient,
                                  ObjectMapper objectMapper,
                                  ChatModelFactory chatModelFactory) {
        super(translatorApiClient, objectMapper);
        this.chatModelFactory = chatModelFactory;
    }

    @Override
    protected void doTranslate(TranslationContext ctx) {
        LlmConfig config = ctx.getLlmConfig();
        List<BubbleData> bubbles = ctx.getValidBubbles();
        int bubbleCount = bubbles.size();

        boolean multimodal = resolveMultimodal(config);
        ChatModel chatModel = chatModelFactory.createChatModel(config);

        log.info("管线 Step 3 - LLM 翻译: provider={}, model={}, multimodal={}, bubbles={}",
                config.getProvider(), config.getModelName(), multimodal, bubbleCount);

        String targetLangDesc = LanguageMapping.toDescription(ctx.getTargetLang());

        // 构建消息序列：System → Few-shot → User
        List<Message> messages = new ArrayList<>();
        messages.add(buildSystemMessage(targetLangDesc));
        messages.add(new UserMessage(FEW_SHOT_INPUT));
        messages.add(new AssistantMessage(FEW_SHOT_OUTPUT));
        messages.add(buildUserMessage(ctx, targetLangDesc, multimodal, bubbleCount));

        // Ollama Qwen 特殊处理
        if (isOllamaQwen(config)) {
            log.debug("检测到 Ollama Qwen 模型，追加 /no_think 指令");
            appendNoThinkToLastUserMessage(messages);
        }

        // 调用模型
        ChatResponse response = chatModel.call(new Prompt(messages));
        String content = response.getResult().getOutput().getText();
        log.debug("LLM 原始响应: {}", content);

        // 解析响应
        List<TranslatedPage> translatedPages = LlmResponseParser.parse(content, objectMapper, bubbleCount);

        if (translatedPages.isEmpty() || translatedPages.getFirst().bubbles().isEmpty()) {
            throw new RuntimeException("LLM 未返回翻译结果");
        }

        ctx.setTranslations(alignTranslations(translatedPages.getFirst(), bubbleCount));
    }

    // ─── Prompt 构建 ────────────────────────────────────────────────

    private Message buildSystemMessage(String targetLangDesc) {
        String text = new PromptTemplate(SYSTEM_PROMPT).render(Map.of(
                "sourceLangDesc", "日文/英文",
                "targetLangDesc", targetLangDesc
        ));
        return new SystemMessage(text);
    }

    private Message buildUserMessage(TranslationContext ctx, String targetLangDesc,
                                     boolean multimodal, int bubbleCount) {
        String ocrText = buildNumberedOcrText(ctx.getValidBubbles());
        Map<String, Object> vars = Map.of(
                "targetLangDesc", targetLangDesc,
                "bubbleCount", String.valueOf(bubbleCount),
                "ocrText", ocrText
        );

        if (multimodal) {
            String text = new PromptTemplate(USER_PROMPT_MULTIMODAL).render(vars);
            var builder = UserMessage.builder().text(text);
            MimeType mimeType = resolveMimeType(getImageFormat(ctx.getFilename()));
            builder.media(new Media(mimeType, new ByteArrayResource(ctx.getImageData())));
            return builder.build();
        } else {
            String text = new PromptTemplate(USER_PROMPT_TEXT).render(vars);
            return new UserMessage(text);
        }
    }

    private String buildNumberedOcrText(List<BubbleData> bubbles) {
        StringBuilder sb = new StringBuilder();
        for (BubbleData b : bubbles) {
            sb.append("<|").append(b.bubbleIndex()).append("|>")
              .append(b.original()).append('\n');
        }
        return sb.toString().trim();
    }

    // ─── 多模态 & Ollama 处理 ───────────────────────────────────────

    private boolean resolveMultimodal(LlmConfig config) {
        if (!Boolean.TRUE.equals(config.getMultimodal())) return false;
        LlmProvider provider = LlmProvider.fromCode(config.getProvider());
        if (!provider.isMultimodalCapable()) {
            log.warn("provider={} 不支持多模态输入，自动降级为纯文本模式", config.getProvider());
            return false;
        }
        return true;
    }

    private boolean isOllamaQwen(LlmConfig config) {
        LlmProvider provider = LlmProvider.fromCode(config.getProvider());
        return provider.getModelType() == LlmProvider.ModelType.OLLAMA
                && config.getModelName() != null
                && config.getModelName().toLowerCase().startsWith("qwen");
    }

    private void appendNoThinkToLastUserMessage(List<Message> messages) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i) instanceof UserMessage existing) {
                messages.set(i, UserMessage.builder()
                        .text(existing.getText() + " /no_think")
                        .media(existing.getMedia().toArray(new Media[0]))
                        .build());
                break;
            }
        }
    }

    private MimeType resolveMimeType(String format) {
        return switch (format != null ? format.toLowerCase() : "png") {
            case "jpeg", "jpg" -> MimeTypeUtils.IMAGE_JPEG;
            case "gif" -> MimeTypeUtils.IMAGE_GIF;
            default -> MimeTypeUtils.IMAGE_PNG;
        };
    }

    // ─── 翻译结果对齐 ──────────────────────────────────────────────

    private List<String> alignTranslations(TranslatedPage tp, int expectedCount) {
        List<TranslatedBubble> translations = tp.bubbles();
        List<String> result = new ArrayList<>();
        for (int i = 0; i < expectedCount; i++) {
            if (i < translations.size()) {
                String translated = translations.get(i).translated();
                result.add(translated != null ? translated : "");
            } else {
                result.add("");
                log.warn("有效 bubble {} 缺少翻译", i);
            }
        }
        if (translations.size() > expectedCount) {
            log.info("LLM 返回多余翻译: 已使用 {}/{}", expectedCount, translations.size());
        }
        return result;
    }
}
