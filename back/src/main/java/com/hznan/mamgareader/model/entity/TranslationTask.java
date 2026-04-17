package com.hznan.mamgareader.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.*;

import java.time.LocalDateTime;

@TableName("translation_tasks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TranslationTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long mangaId;

    private Long configId;

    private Integer totalPages;

    @Builder.Default
    private Integer completedPages = 0;

    @Builder.Default
    private Integer failedPages = 0;

    @Builder.Default
    private String status = "pending";

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
