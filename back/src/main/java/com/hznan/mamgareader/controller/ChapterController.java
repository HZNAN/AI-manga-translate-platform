package com.hznan.mamgareader.controller;

import com.hznan.mamgareader.interceptor.AuthInterceptor;
import com.hznan.mamgareader.model.dto.CreateChapterRequest;
import com.hznan.mamgareader.model.dto.ReorderChaptersRequest;
import com.hznan.mamgareader.model.dto.UpdateChapterRequest;
import com.hznan.mamgareader.model.entity.Chapter;
import com.hznan.mamgareader.model.entity.MangaPage;
import com.hznan.mamgareader.model.vo.ApiResponse;
import com.hznan.mamgareader.service.ChapterService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/mangas/{mangaId}/chapters")
@RequiredArgsConstructor
public class ChapterController {

    private final ChapterService chapterService;

    @GetMapping
    public ApiResponse<List<Chapter>> list(
            HttpServletRequest request,
            @PathVariable Long mangaId) {
        return ApiResponse.ok(chapterService.listChapters(mangaId, getUserId(request)));
    }

    @GetMapping("/{chapterId}")
    public ApiResponse<Chapter> get(
            HttpServletRequest request,
            @PathVariable Long mangaId,
            @PathVariable Long chapterId) {
        return ApiResponse.ok(chapterService.getChapter(mangaId, chapterId, getUserId(request)));
    }

    @PostMapping
    public ApiResponse<Chapter> create(
            HttpServletRequest request,
            @PathVariable Long mangaId,
            @Valid @RequestBody CreateChapterRequest req) {
        return ApiResponse.ok(chapterService.createChapter(mangaId, getUserId(request), req));
    }

    @PutMapping("/{chapterId}")
    public ApiResponse<Chapter> update(
            HttpServletRequest request,
            @PathVariable Long mangaId,
            @PathVariable Long chapterId,
            @RequestBody UpdateChapterRequest req) {
        return ApiResponse.ok(chapterService.updateChapter(mangaId, chapterId, getUserId(request), req));
    }

    @DeleteMapping("/{chapterId}")
    public ApiResponse<Void> delete(
            HttpServletRequest request,
            @PathVariable Long mangaId,
            @PathVariable Long chapterId) {
        chapterService.deleteChapter(mangaId, chapterId, getUserId(request));
        return ApiResponse.ok();
    }

    @PostMapping("/reorder")
    public ApiResponse<Void> reorder(
            HttpServletRequest request,
            @PathVariable Long mangaId,
            @Valid @RequestBody ReorderChaptersRequest req) {
        chapterService.reorderChapters(mangaId, getUserId(request), req.chapterIds());
        return ApiResponse.ok();
    }

    @GetMapping("/{chapterId}/pages")
    public ApiResponse<List<MangaPage>> getPages(
            HttpServletRequest request,
            @PathVariable Long mangaId,
            @PathVariable Long chapterId) {
        return ApiResponse.ok(chapterService.getChapterPages(mangaId, chapterId, getUserId(request)));
    }

    @GetMapping("/all-pages")
    public ApiResponse<Map<Long, List<MangaPage>>> getAllPages(
            HttpServletRequest request,
            @PathVariable Long mangaId) {
        return ApiResponse.ok(chapterService.getAllPagesGrouped(mangaId, getUserId(request)));
    }

    private Long getUserId(HttpServletRequest request) {
        return (Long) request.getAttribute(AuthInterceptor.CURRENT_USER_ID);
    }
}
