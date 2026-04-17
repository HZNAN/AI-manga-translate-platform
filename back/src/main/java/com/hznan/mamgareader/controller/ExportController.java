package com.hznan.mamgareader.controller;

import com.hznan.mamgareader.interceptor.AuthInterceptor;
import com.hznan.mamgareader.model.dto.ExportRequest;
import com.hznan.mamgareader.service.ExportService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

@RestController
@RequestMapping("/api/export")
@RequiredArgsConstructor
public class ExportController {

    private final ExportService exportService;

    @PostMapping("/zip")
    public ResponseEntity<byte[]> exportZip(
            HttpServletRequest request,
            @Valid @RequestBody ExportRequest req) throws IOException {
        Long userId = getUserId(request);
        boolean onlyTranslated = Boolean.TRUE.equals(req.onlyTranslated());
        byte[] data = exportService.exportZip(req.mangaId(), userId, onlyTranslated);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=manga_export.zip")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(data);
    }

    @PostMapping("/pdf")
    public ResponseEntity<byte[]> exportPdf(
            HttpServletRequest request,
            @Valid @RequestBody ExportRequest req) throws IOException {
        Long userId = getUserId(request);
        boolean onlyTranslated = Boolean.TRUE.equals(req.onlyTranslated());
        byte[] data = exportService.exportPdf(req.mangaId(), userId, onlyTranslated);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=manga_export.pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(data);
    }

    private Long getUserId(HttpServletRequest request) {
        return (Long) request.getAttribute(AuthInterceptor.CURRENT_USER_ID);
    }
}
