package com.hznan.mamgareader.model.dto;

import jakarta.validation.constraints.NotNull;

public record ExportRequest(
        @NotNull Long mangaId,
        Boolean onlyTranslated
) {
}
