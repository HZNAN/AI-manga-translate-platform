package com.hznan.mamgareader.model.dto;

import jakarta.validation.constraints.NotNull;

public record TranslatePageRequest(
        @NotNull Long pageId
) {
}
