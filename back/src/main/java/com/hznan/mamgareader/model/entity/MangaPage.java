package com.hznan.mamgareader.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.*;

import java.time.LocalDateTime;

@TableName("manga_pages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MangaPage {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long mangaId;

    private Long chapterId;

    private Integer pageNumber;

    private String originalFilename;

    private String imagePath;

    private String thumbnailPath;

    private Integer width;

    private Integer height;

    private Long fileSize;

    @Builder.Default
    private Boolean isTranslated = false;

    private String translatedImagePath;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
