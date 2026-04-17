package com.hznan.mamgareader.model.dto;

import jakarta.validation.constraints.NotNull;

public record OcrRegionRequest(
        @NotNull Long pageId,
        @NotNull RegionBox region
) {
}
