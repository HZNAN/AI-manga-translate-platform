package com.hznan.mamgareader.controller;

import com.hznan.mamgareader.interceptor.AuthInterceptor;
import com.hznan.mamgareader.model.dto.BatchTranslateRequest;
import com.hznan.mamgareader.model.dto.TranslatePageRequest;
import com.hznan.mamgareader.model.entity.TranslationRecord;
import com.hznan.mamgareader.model.entity.TranslationTask;
import com.hznan.mamgareader.model.vo.ApiResponse;
import com.hznan.mamgareader.service.TranslateService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/translate")
@RequiredArgsConstructor
public class TranslateController {

    private final TranslateService translateService;

    @PostMapping("/page")
    public ApiResponse<TranslationRecord> translatePage(
            HttpServletRequest request,
            @Valid @RequestBody TranslatePageRequest req) {
        Long userId = getUserId(request);
        return ApiResponse.ok(translateService.translatePage(userId, req));
    }

    @PostMapping("/page/json")
    public ApiResponse<TranslationRecord> translatePageJson(
            HttpServletRequest request,
            @Valid @RequestBody TranslatePageRequest req) {
        Long userId = getUserId(request);
        return ApiResponse.ok(translateService.translatePageJson(userId, req));
    }

    @PostMapping("/batch")
    public ApiResponse<TranslationTask> batchTranslate(
            HttpServletRequest request,
            @Valid @RequestBody BatchTranslateRequest req) {
        Long userId = getUserId(request);
        return ApiResponse.ok(translateService.createBatchTask(
                userId, req.mangaId(), req.pageIds(), req.forceRetranslate()));
    }

    @GetMapping("/tasks")
    public ApiResponse<List<TranslationTask>> getTasks(
            HttpServletRequest request,
            @RequestParam(required = false) Long mangaId) {
        Long userId = getUserId(request);
        return ApiResponse.ok(translateService.getTasks(userId, mangaId));
    }

    @GetMapping("/tasks/{taskId}")
    public ApiResponse<TranslationTask> getTask(
            HttpServletRequest request,
            @PathVariable Long taskId) {
        Long userId = getUserId(request);
        return ApiResponse.ok(translateService.getTask(taskId, userId));
    }

    @DeleteMapping("/tasks/{taskId}")
    public ApiResponse<Void> cancelTask(
            HttpServletRequest request,
            @PathVariable Long taskId) {
        Long userId = getUserId(request);
        translateService.cancelTask(taskId, userId);
        return ApiResponse.ok();
    }

    @GetMapping("/records")
    public ApiResponse<List<TranslationRecord>> getRecords(
            HttpServletRequest request,
            @RequestParam(required = false) Long mangaId,
            @RequestParam(required = false) Long chapterId,
            @RequestParam(required = false) Long pageId) {
        Long userId = getUserId(request);
        return ApiResponse.ok(translateService.getRecords(userId, mangaId, chapterId, pageId));
    }

    @GetMapping("/records/{id}/image")
    public ResponseEntity<byte[]> getRecordImage(
            @PathVariable Long id) throws IOException {
        byte[] data = translateService.getRecordImagePublic(id);
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .body(data);
    }

    @GetMapping("/records/{id}/status")
    public ApiResponse<TranslationRecord> getRecordStatus(@PathVariable Long id) {
        return ApiResponse.ok(translateService.getRecord(id));
    }

    @PostMapping("/records/{id}/rollback")
    public ApiResponse<Void> rollbackToRecord(
            HttpServletRequest request,
            @PathVariable Long id) {
        Long userId = getUserId(request);
        translateService.rollbackToRecord(id, userId);
        return ApiResponse.ok();
    }

    @GetMapping("/queue-size")
    public ApiResponse<Integer> getQueueSize() {
        return ApiResponse.ok(translateService.getQueueSize());
    }

    private Long getUserId(HttpServletRequest request) {
        return (Long) request.getAttribute(AuthInterceptor.CURRENT_USER_ID);
    }
}
