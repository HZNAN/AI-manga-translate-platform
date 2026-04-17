package com.hznan.mamgareader.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.hznan.mamgareader.handler.JsonbTypeHandler;
import lombok.*;

import java.time.LocalDateTime;
import java.util.Map;

@TableName(value = "translation_records", autoResultMap = true)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TranslationRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long mangaId;

    private Long pageId;

    private Long chapterId;

    private Integer pageNumber;

    private Long configId;

    private Long taskId;

    @Builder.Default
    private String status = "queued";

    private String translatedImagePath;

    @TableField(typeHandler = JsonbTypeHandler.class)
    private Map<String, Object> translationJson;

    @TableField(typeHandler = JsonbTypeHandler.class)
    private Map<String, Object> configSnapshot;

    private String errorMessage;

    private Integer durationMs;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    private LocalDateTime completedAt;
}
