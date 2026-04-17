package com.hznan.mamgareader.model.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateChapterRequest(
        @NotBlank String title
) {
}
