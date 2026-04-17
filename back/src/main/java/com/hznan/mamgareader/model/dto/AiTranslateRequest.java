package com.hznan.mamgareader.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record AiTranslateRequest(
        @NotNull Long pageId,
        @NotNull List<RegionBox> regions,
        @NotBlank String targetLang,
        @NotNull Long llmConfigId,
        String prompt
) {
}
