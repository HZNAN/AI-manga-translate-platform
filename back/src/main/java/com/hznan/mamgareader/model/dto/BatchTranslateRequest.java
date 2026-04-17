package com.hznan.mamgareader.model.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public record BatchTranslateRequest(
        @NotNull Long mangaId,
        List<Long> pageIds,
        Boolean forceRetranslate
) {
}
