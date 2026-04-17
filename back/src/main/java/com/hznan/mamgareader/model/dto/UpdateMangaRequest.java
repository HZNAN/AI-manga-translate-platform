package com.hznan.mamgareader.model.dto;

public record UpdateMangaRequest(
        String title,
        String author,
        String description,
        String tags,
        String readingDirection,
        Long activeConfigId
) {
}
