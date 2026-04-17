package com.hznan.mamgareader.model.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public record ReorderChaptersRequest(
        @NotNull List<Long> chapterIds
) {
}
