package com.hznan.mamgareader.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.hznan.mamgareader.handler.JsonbTypeHandler;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@TableName(value = "translate_configs", autoResultMap = true)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TranslateConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String name;

    @Builder.Default
    private Boolean isDefault = false;

    @Builder.Default
    private String targetLang = "CHS";

    @Builder.Default
    private String translator = "none";

    @Builder.Default
    private String detector = "ctd";

    @Builder.Default
    private Integer detectionSize = 1536;

    @Builder.Default
    private BigDecimal textThreshold = new BigDecimal("0.50");

    @Builder.Default
    private BigDecimal boxThreshold = new BigDecimal("0.80");

    @Builder.Default
    private BigDecimal unclipRatio = new BigDecimal("2.5");

    @Builder.Default
    private String ocr = "48px";

    @Builder.Default
    private String sourceLang = "japanese";

    @Builder.Default
    private Boolean useMocrMerge = false;

    @Builder.Default
    private String inpainter = "lama_mpe";

    @Builder.Default
    private Integer inpaintingSize = 2560;

    @Builder.Default
    private String inpaintingPrecision = "bf16";

    @Builder.Default
    private String renderer = "default";

    @Builder.Default
    private String alignment = "auto";

    @Builder.Default
    private String direction = "auto";

    @Builder.Default
    private Integer fontSizeOffset = 0;

    @Builder.Default
    private Integer maskDilationOffset = 20;

    @Builder.Default
    private Integer kernelSize = 5;

    @Builder.Default
    private String upscaler = "esrgan";

    @Builder.Default
    private Integer upscaleRatio = 2;

    @Builder.Default
    private String colorizer = "none";

    private Long llmConfigId;

    @TableField(typeHandler = JsonbTypeHandler.class)
    private Map<String, Object> extraConfig;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
