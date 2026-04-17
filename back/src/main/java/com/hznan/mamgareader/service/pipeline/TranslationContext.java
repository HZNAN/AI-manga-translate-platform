package com.hznan.mamgareader.service.pipeline;

import com.hznan.mamgareader.model.entity.LlmConfig;
import com.hznan.mamgareader.model.entity.TranslateConfig;
import com.hznan.mamgareader.service.TranslatorApiClient.DetectOcrResult;
import com.hznan.mamgareader.service.translation.TranslationDTOs.BubbleData;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 翻译管线上下文 — 在管线各步骤之间传递输入数据和中间结果。
 * <p>
 * 使用 Builder 模式构建初始输入，各步骤通过 setter 写入中间结果，
 * 下游步骤通过 getter 读取上游结果，形成数据流。
 * </p>
 */
@Data
@Builder
public class TranslationContext {

    // ─── 输入 ────────────────────────────────────────────────────────
    private final byte[] imageData;
    private final String filename;
    private final TranslateConfig translateConfig;
    private final LlmConfig llmConfig;
    private final String targetLang;

    // ─── 步骤间中间结果 ──────────────────────────────────────────────
    private DetectOcrResult ocrResult;
    private List<Integer> validIndices;
    private List<BubbleData> validBubbles;
    private List<String> translations;
    private List<int[]> filteredCoords;
    private String filteredRegionsJson;
    private byte[] cleanImage;
    private byte[] finalImage;
}
