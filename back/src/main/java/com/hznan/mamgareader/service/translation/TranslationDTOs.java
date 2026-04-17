package com.hznan.mamgareader.service.translation;

import java.util.List;

/**
 * 翻译流程中使用的数据传输对象。
 * <p>
 * 使用 Java 17 Record 替代传统 POJO，减少样板代码。
 * 所有 DTO 均为不可变值对象，线程安全。
 * </p>
 */
public final class TranslationDTOs {

    private TranslationDTOs() {}

    /** 单个页面的 OCR 数据，包含原图（可选，多模态时使用）和识别出的气泡文本 */
    public record PageOcrData(int imageIndex, byte[] imageBytes, String imageFormat, List<BubbleData> bubbles) {}

    /** 单个气泡的 OCR 原文 */
    public record BubbleData(int bubbleIndex, String original) {}

    /** 翻译后的单个气泡 */
    public record TranslatedBubble(int bubbleIndex, String original, String translated) {}

    /** 翻译后的单页结果 */
    public record TranslatedPage(int imageIndex, List<TranslatedBubble> bubbles) {}
}
