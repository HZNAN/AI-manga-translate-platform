package com.hznan.mamgareader.model.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateMangaRequest(
        @NotBlank String title,
        String author,
        String description
) {
}
