package com.hznan.mamgareader.model.vo;

public record AiTranslateResult(
        int regionIndex,
        String original,
        String translated
) {
}
