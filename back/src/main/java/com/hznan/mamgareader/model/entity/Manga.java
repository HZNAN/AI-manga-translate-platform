package com.hznan.mamgareader.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.*;

import java.time.LocalDateTime;

@TableName("mangas")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Manga {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String title;

    private String author;

    private String description;

    private String coverUrl;

    @Builder.Default
    private Integer pageCount = 0;

    private String tags;

    @Builder.Default
    private String readingDirection = "rtl";

    @Builder.Default
    private Integer lastReadPage = 0;

    private LocalDateTime lastReadAt;

    private Long activeConfigId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
