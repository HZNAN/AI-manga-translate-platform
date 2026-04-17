package com.hznan.mamgareader.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.*;

import java.time.LocalDateTime;

@TableName("llm_configs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LlmConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String name;

    private String provider;

    private String apiKey;

    private String modelName;

    private String baseUrl;

    @Builder.Default
    private Boolean isDefault = false;

    @Builder.Default
    private Boolean multimodal = false;

    private String secretKey;

    @Builder.Default
    private Boolean isSystem = false;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
