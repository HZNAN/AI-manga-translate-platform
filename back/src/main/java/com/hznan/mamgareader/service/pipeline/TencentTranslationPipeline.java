package com.hznan.mamgareader.service.pipeline;

import com.hznan.mamgareader.service.TencentTranslateClient;
import com.hznan.mamgareader.service.TranslatorApiClient;
import com.hznan.mamgareader.service.translation.TranslationDTOs.BubbleData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * 腾讯云机器翻译管线 — 覆写 {@link #doTranslate} 钩子，逐气泡调用腾讯 TMT API。
 * <p>
 * 与 {@link LlmTranslationPipeline} 的区别：不构建 Prompt、不使用 ChatModel，
 * 而是逐条调用传统的机器翻译 REST API。
 * 管线骨架（检测+OCR → 过滤 → 翻译 → 修复 → 渲染）完全复用基类的默认实现。
 * </p>
 */
@Slf4j
@Component
public class TencentTranslationPipeline extends AbstractTranslationPipeline {

    private final TencentTranslateClient tencentClient;

    public TencentTranslationPipeline(TranslatorApiClient translatorApiClient,
                                      ObjectMapper objectMapper,
                                      TencentTranslateClient tencentClient) {
        super(translatorApiClient, objectMapper);
        this.tencentClient = tencentClient;
    }

    @Override
    protected void doTranslate(TranslationContext ctx) {
        List<BubbleData> bubbles = ctx.getValidBubbles();
        log.info("管线 Step 3 - 腾讯机器翻译: bubbles={}, targetLang={}", bubbles.size(), ctx.getTargetLang());

        List<String> translations = new ArrayList<>();
        for (BubbleData bubble : bubbles) {
            try {
                String translated = tencentClient.translate(
                        ctx.getLlmConfig(), bubble.original(), "auto", ctx.getTargetLang());
                translations.add(translated);
            } catch (Exception e) {
                log.warn("腾讯翻译失败 bubble={}: {}", bubble.bubbleIndex(), e.getMessage());
                translations.add(bubble.original());
            }
        }
        ctx.setTranslations(translations);
    }
}
