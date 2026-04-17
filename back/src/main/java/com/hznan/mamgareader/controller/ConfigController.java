package com.hznan.mamgareader.controller;

import com.hznan.mamgareader.interceptor.AuthInterceptor;
import com.hznan.mamgareader.model.entity.TranslateConfig;
import com.hznan.mamgareader.model.vo.ApiResponse;
import com.hznan.mamgareader.service.ConfigService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/configs")
@RequiredArgsConstructor
public class ConfigController {

    private final ConfigService configService;

    @GetMapping
    public ApiResponse<List<TranslateConfig>> list(HttpServletRequest request) {
        Long userId = getUserId(request);
        return ApiResponse.ok(configService.getUserConfigs(userId));
    }

    @PostMapping
    public ApiResponse<TranslateConfig> create(HttpServletRequest request,
                                                @RequestBody TranslateConfig config) {
        Long userId = getUserId(request);
        return ApiResponse.ok(configService.create(userId, config));
    }

    @PutMapping("/{id}")
    public ApiResponse<TranslateConfig> update(HttpServletRequest request,
                                                @PathVariable Long id,
                                                @RequestBody TranslateConfig config) {
        Long userId = getUserId(request);
        return ApiResponse.ok(configService.update(id, userId, config));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(HttpServletRequest request, @PathVariable Long id) {
        Long userId = getUserId(request);
        configService.delete(id, userId);
        return ApiResponse.ok();
    }

    @GetMapping("/presets")
    public ApiResponse<List<TranslateConfig>> presets() {
        return ApiResponse.ok(configService.getPresets());
    }

    private Long getUserId(HttpServletRequest request) {
        return (Long) request.getAttribute(AuthInterceptor.CURRENT_USER_ID);
    }
}
