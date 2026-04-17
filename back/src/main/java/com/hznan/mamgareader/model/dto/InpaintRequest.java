package com.hznan.mamgareader.model.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public record InpaintRequest(
        @NotNull Long pageId,
        @NotNull List<RegionBox> regions
) {
}
