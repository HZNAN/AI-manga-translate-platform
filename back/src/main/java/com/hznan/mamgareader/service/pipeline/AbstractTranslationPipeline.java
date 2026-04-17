package com.hznan.mamgareader.service.pipeline;

import com.hznan.mamgareader.service.TranslatorApiClient;
import com.hznan.mamgareader.service.TranslatorApiClient.DetectOcrResult;
import com.hznan.mamgareader.service.translation.TranslationDTOs.BubbleData;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * 翻译管线模板方法基类 — 定义「检测+OCR → 过滤 → 翻译 → 修复 → 渲染」的骨架流程。
 * <p>
 * <b>模板方法模式（Template Method）：</b>{@link #execute(TranslationContext)} 为 {@code final}，
 * 固定了五步管线骨架。子类通过覆写钩子方法（尤其是 {@link #doTranslate}）来实现差异化行为，
 * 而不改变整体流程。类比支付场景中 {@code AbstractPaymentTemplate.pay()} 定义
 * 「验证 → 创建订单 → 处理支付 → 处理结果」的骨架。
 * </p>
 *
 * <h3>管线骨架</h3>
 * <ol>
 *   <li>{@link #doDetectAndOcr} — 调用 Python Step API 进行文本检测与 OCR 识别</li>
 *   <li>{@link #doFilterOcr} — 过滤 OCR 噪声（单字符、纯标点等），构建有效气泡列表</li>
 *   <li>{@link #doTranslate} — <b>抽象钩子</b>：由子类实现具体翻译逻辑（LLM / 机器翻译）</li>
 *   <li>{@link #doInpaint} — 调用 Python Step API 对有效区域进行文字擦除</li>
 *   <li>{@link #doRender} — 调用 Python Step API 将译文渲染回图片</li>
 * </ol>
 *
 * @see LlmTranslationPipeline
 * @see TencentTranslationPipeline
 */
@Slf4j
public abstract class AbstractTranslationPipeline {

    protected final TranslatorApiClient translatorApiClient;
    protected final ObjectMapper objectMapper;

    protected AbstractTranslationPipeline(TranslatorApiClient translatorApiClient,
                                          ObjectMapper objectMapper) {
        this.translatorApiClient = translatorApiClient;
        this.objectMapper = objectMapper;
    }

    /**
     * 模板方法 — 执行完整的翻译管线。
     * <p>
     * 返回最终渲染图片字节数组；返回 {@code null} 表示未检测到文本，应使用原图。
     * </p>
     *
     * @param ctx 管线上下文（输入数据 + 各步骤中间结果）
     * @return 翻译渲染后的图片，或 null（无文本）
     */
    public final byte[] execute(TranslationContext ctx) {
        // Step 1: Detection + OCR
        doDetectAndOcr(ctx);
        if (ctx.getOcrResult() == null || ctx.getOcrResult().regionCount() == 0) {
            log.info("管线: 未检测到文本区域");
            return null;
        }

        // Step 2: Filter OCR results
        doFilterOcr(ctx);
        if (ctx.getValidBubbles() == null || ctx.getValidBubbles().isEmpty()) {
            log.info("管线: 过滤后无有效文本");
            return null;
        }

        // Step 3: Translate (abstract hook)
        doTranslate(ctx);

        // Step 4: Inpaint
        doInpaint(ctx);

        // Step 5: Render
        doRender(ctx);

        return ctx.getFinalImage();
    }

    // ═══════════════════════════════════════════════════════════════════
    // Step 1: Detection + OCR（默认实现：调用 Python Step API）
    // ═══════════════════════════════════════════════════════════════════

    protected void doDetectAndOcr(TranslationContext ctx) {
        log.info("管线 Step 1 - Detection+OCR");
        DetectOcrResult ocr = translatorApiClient.detectAndOcr(
                ctx.getImageData(), ctx.getFilename(), ctx.getTranslateConfig());
        ctx.setOcrResult(ocr);
        log.info("OCR 完成: 识别 {} 个文本区域", ocr.regionCount());
    }

    // ═══════════════════════════════════════════════════════════════════
    // Step 2: Filter OCR（默认实现：过滤噪声 + 构建有效气泡和坐标）
    // ═══════════════════════════════════════════════════════════════════

    protected void doFilterOcr(TranslationContext ctx) {
        DetectOcrResult ocr = ctx.getOcrResult();
        List<Integer> validIndices = new ArrayList<>();
        List<BubbleData> bubbles = new ArrayList<>();
        int seqIndex = 1;

        for (int i = 0; i < ocr.originalTexts().size(); i++) {
            String text = ocr.originalTexts().get(i);
            if (isValidOcrText(text)) {
                validIndices.add(i);
                bubbles.add(new BubbleData(seqIndex++, text));
            }
        }

        int skipped = ocr.regionCount() - validIndices.size();
        if (skipped > 0) {
            log.info("OCR 过滤: 总计={}, 有效={}, 跳过={}", ocr.regionCount(), validIndices.size(), skipped);
        }

        // 构建过滤后的坐标和区域数据
        List<int[]> filteredCoords = new ArrayList<>();
        for (int idx : validIndices) {
            filteredCoords.add(ocr.bubbleCoords().get(idx));
        }

        String filteredRegionsJson;
        try {
            @SuppressWarnings("unchecked")
            List<Object> allRegions = objectMapper.readValue(ocr.textRegionsDataJson(), List.class);
            List<Object> filteredRegions = new ArrayList<>();
            for (int idx : validIndices) {
                filteredRegions.add(allRegions.get(idx));
            }
            filteredRegionsJson = objectMapper.writeValueAsString(filteredRegions);
        } catch (Exception e) {
            log.warn("过滤 textRegionsData 失败，使用全量: {}", e.getMessage());
            filteredRegionsJson = ocr.textRegionsDataJson();
        }

        ctx.setValidIndices(validIndices);
        ctx.setValidBubbles(bubbles);
        ctx.setFilteredCoords(filteredCoords);
        ctx.setFilteredRegionsJson(filteredRegionsJson);
    }

    // ═══════════════════════════════════════════════════════════════════
    // Step 3: Translate（抽象钩子 — 子类必须实现）
    // ═══════════════════════════════════════════════════════════════════

    /**
     * 翻译钩子 — 子类实现具体的翻译逻辑。
     * <p>
     * 实现类应从 {@code ctx.getValidBubbles()} 获取待翻译文本，
     * 翻译完成后通过 {@code ctx.setTranslations()} 写入结果。
     * </p>
     */
    protected abstract void doTranslate(TranslationContext ctx);

    // ═══════════════════════════════════════════════════════════════════
    // Step 4: Inpaint（默认实现：调用 Python Step API）
    // ═══════════════════════════════════════════════════════════════════

    protected void doInpaint(TranslationContext ctx) {
        try {
            String coordsJson = objectMapper.writeValueAsString(ctx.getFilteredCoords());
            log.info("管线 Step 4 - Inpaint: regions={}", ctx.getFilteredCoords().size());
            byte[] cleanImage = translatorApiClient.inpaintStep(
                    ctx.getImageData(), ctx.getFilename(),
                    "", coordsJson, ctx.getTranslateConfig());
            ctx.setCleanImage(cleanImage);
        } catch (Exception e) {
            throw new RuntimeException("修复失败: " + e.getMessage(), e);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Step 5: Render（默认实现：调用 Python Step API）
    // ═══════════════════════════════════════════════════════════════════

    protected void doRender(TranslationContext ctx) {
        try {
            String translationsJson = objectMapper.writeValueAsString(ctx.getTranslations());
            log.info("管线 Step 5 - Render: regions={}", ctx.getTranslations().size());
            byte[] finalImage = translatorApiClient.renderStep(
                    ctx.getCleanImage(), ctx.getFilename(),
                    translationsJson, ctx.getFilteredRegionsJson(),
                    ctx.getTranslateConfig());
            ctx.setFinalImage(finalImage);
        } catch (Exception e) {
            throw new RuntimeException("渲染失败: " + e.getMessage(), e);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // 工具方法
    // ═══════════════════════════════════════════════════════════════════

    protected boolean isValidOcrText(String text) {
        if (text == null || text.isBlank()) return false;
        String stripped = text.replaceAll("[\\p{Punct}\\p{IsPunctuation}\\s]", "");
        return stripped.length() >= 2;
    }

    protected String getImageFormat(String filename) {
        if (filename == null) return "png";
        String lower = filename.toLowerCase();
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "jpeg";
        if (lower.endsWith(".webp")) return "webp";
        return "png";
    }
}
