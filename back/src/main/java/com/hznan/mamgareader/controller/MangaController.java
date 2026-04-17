package com.hznan.mamgareader.controller;

import com.hznan.mamgareader.interceptor.AuthInterceptor;
import com.hznan.mamgareader.model.dto.CreateMangaRequest;
import com.hznan.mamgareader.model.dto.ReadingProgressRequest;
import com.hznan.mamgareader.model.dto.UpdateMangaRequest;
import com.hznan.mamgareader.model.entity.Manga;
import com.hznan.mamgareader.model.entity.MangaPage;
import com.hznan.mamgareader.model.vo.ApiResponse;
import com.hznan.mamgareader.model.vo.PageResult;
import com.hznan.mamgareader.service.MangaService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/mangas")
@RequiredArgsConstructor
public class MangaController {

    private final MangaService mangaService;

    @GetMapping
    public ApiResponse<PageResult<Manga>> list(
            HttpServletRequest request,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String sort) {
        Long userId = getUserId(request);
        return ApiResponse.ok(mangaService.list(userId, page, size, keyword, sort));
    }

    @GetMapping("/{id}")
    public ApiResponse<Manga> getById(HttpServletRequest request, @PathVariable Long id) {
        Long userId = getUserId(request);
        return ApiResponse.ok(mangaService.getById(id, userId));
    }

    @PostMapping
    public ApiResponse<Manga> create(HttpServletRequest request,
                                      @Valid @RequestBody CreateMangaRequest req) {
        Long userId = getUserId(request);
        return ApiResponse.ok(mangaService.create(userId, req));
    }

    @PutMapping("/{id}")
    public ApiResponse<Manga> update(HttpServletRequest request,
                                      @PathVariable Long id,
                                      @RequestBody UpdateMangaRequest req) {
        Long userId = getUserId(request);
        return ApiResponse.ok(mangaService.update(id, userId, req));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(HttpServletRequest request, @PathVariable Long id) {
        Long userId = getUserId(request);
        mangaService.delete(id, userId);
        return ApiResponse.ok();
    }

    @GetMapping("/{id}/pages")
    public ApiResponse<List<MangaPage>> getPages(HttpServletRequest request, @PathVariable Long id) {
        Long userId = getUserId(request);
        return ApiResponse.ok(mangaService.getPages(id, userId));
    }

    @PostMapping("/{id}/chapters/{chapterId}/pages")
    public ApiResponse<List<MangaPage>> uploadPages(
            HttpServletRequest request,
            @PathVariable Long id,
            @PathVariable Long chapterId,
            @RequestParam("files") MultipartFile[] files) throws IOException {
        Long userId = getUserId(request);
        return ApiResponse.ok(mangaService.uploadPages(id, chapterId, userId, files));
    }

    @PostMapping("/upload-archive")
    public ApiResponse<Manga> uploadArchive(
            HttpServletRequest request,
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String author) throws IOException {
        Long userId = getUserId(request);
        return ApiResponse.ok(mangaService.uploadArchive(userId, file, title, author));
    }

    @GetMapping("/{id}/pages/{pageNum}/image")
    public ResponseEntity<byte[]> getPageImage(
            @PathVariable Long id,
            @PathVariable int pageNum) throws IOException {
        byte[] data = mangaService.getPageImagePublic(id, pageNum);
        return ResponseEntity.ok()
                .contentType(detectMediaType(data))
                .body(data);
    }

    @GetMapping("/{id}/pages/{pageNum}/thumbnail")
    public ResponseEntity<byte[]> getPageThumbnail(
            @PathVariable Long id,
            @PathVariable int pageNum) throws IOException {
        byte[] data = mangaService.getPageThumbnailPublic(id, pageNum);
        return ResponseEntity.ok()
                .contentType(detectMediaType(data))
                .body(data);
    }

    @GetMapping("/{id}/pages/{pageNum}/translated-image")
    public ResponseEntity<byte[]> getPageTranslatedImage(
            @PathVariable Long id,
            @PathVariable int pageNum) throws IOException {
        byte[] data = mangaService.getPageTranslatedImagePublic(id, pageNum);
        return ResponseEntity.ok()
                .contentType(detectMediaType(data))
                .body(data);
    }

    @GetMapping("/page-by-id/{pageId}/image")
    public ResponseEntity<byte[]> getPageImageById(@PathVariable Long pageId) throws IOException {
        byte[] data = mangaService.getPageImageByIdPublic(pageId);
        return ResponseEntity.ok().contentType(detectMediaType(data)).body(data);
    }

    @GetMapping("/page-by-id/{pageId}/thumbnail")
    public ResponseEntity<byte[]> getPageThumbnailById(@PathVariable Long pageId) throws IOException {
        byte[] data = mangaService.getPageThumbnailByIdPublic(pageId);
        return ResponseEntity.ok().contentType(detectMediaType(data)).body(data);
    }

    @GetMapping("/page-by-id/{pageId}/translated-image")
    public ResponseEntity<byte[]> getPageTranslatedImageById(@PathVariable Long pageId) throws IOException {
        byte[] data = mangaService.getPageTranslatedImageByIdPublic(pageId);
        return ResponseEntity.ok().contentType(detectMediaType(data)).body(data);
    }

    @PutMapping("/{id}/active-config/{configId}")
    public ApiResponse<Manga> setActiveConfig(HttpServletRequest request,
                                               @PathVariable Long id,
                                               @PathVariable Long configId) {
        Long userId = getUserId(request);
        return ApiResponse.ok(mangaService.setActiveConfig(id, userId, configId));
    }

    @DeleteMapping("/{id}/active-config")
    public ApiResponse<Manga> clearActiveConfig(HttpServletRequest request,
                                                 @PathVariable Long id) {
        Long userId = getUserId(request);
        return ApiResponse.ok(mangaService.clearActiveConfig(id, userId));
    }

    @PutMapping("/{id}/reading-progress")
    public ApiResponse<Void> updateReadingProgress(
            HttpServletRequest request,
            @PathVariable Long id,
            @RequestBody ReadingProgressRequest req) {
        Long userId = getUserId(request);
        mangaService.updateReadingProgress(id, userId, req);
        return ApiResponse.ok();
    }

    private Long getUserId(HttpServletRequest request) {
        return (Long) request.getAttribute(AuthInterceptor.CURRENT_USER_ID);
    }

    private MediaType detectMediaType(byte[] data) {
        if (data.length >= 8) {
            if (data[0] == (byte) 0x89 && data[1] == (byte) 0x50) {
                return MediaType.IMAGE_PNG;
            }
            if (data[0] == (byte) 0x47 && data[1] == (byte) 0x49) {
                return MediaType.IMAGE_GIF;
            }
        }
        return MediaType.IMAGE_JPEG;
    }
}
