package com.hznan.mamgareader.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hznan.mamgareader.exception.BusinessException;
import com.hznan.mamgareader.interceptor.AuthInterceptor;
import com.hznan.mamgareader.mapper.LlmConfigMapper;
import com.hznan.mamgareader.model.entity.LlmConfig;
import com.hznan.mamgareader.model.vo.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/llm-configs")
@RequiredArgsConstructor
public class LlmConfigController {

    private final LlmConfigMapper llmConfigMapper;

    @GetMapping
    public ApiResponse<List<LlmConfig>> list(HttpServletRequest request) {
        Long userId = getUserId(request);
        List<LlmConfig> systemConfigs = llmConfigMapper.selectList(
                new LambdaQueryWrapper<LlmConfig>()
                        .eq(LlmConfig::getIsSystem, true)
                        .orderByAsc(LlmConfig::getId));

        List<LlmConfig> userConfigs = llmConfigMapper.selectList(
                new LambdaQueryWrapper<LlmConfig>()
                        .eq(LlmConfig::getUserId, userId)
                        .eq(LlmConfig::getIsSystem, false)
                        .orderByDesc(LlmConfig::getIsDefault)
                        .orderByDesc(LlmConfig::getCreatedAt));
        userConfigs.forEach(c -> c.setApiKey(maskApiKey(c.getApiKey())));

        List<LlmConfig> all = new java.util.ArrayList<>(systemConfigs);
        all.addAll(userConfigs);
        return ApiResponse.ok(all);
    }

    @GetMapping("/{id}")
    public ApiResponse<LlmConfig> getById(HttpServletRequest request, @PathVariable Long id) {
        LlmConfig config = getAndCheckOwner(id, getUserId(request));
        config.setApiKey(maskApiKey(config.getApiKey()));
        return ApiResponse.ok(config);
    }

    @PostMapping
    public ApiResponse<LlmConfig> create(HttpServletRequest request, @RequestBody LlmConfig config) {
        Long userId = getUserId(request);
        config.setId(null);
        config.setUserId(userId);

        if (Boolean.TRUE.equals(config.getIsDefault())) {
            clearDefaultFlag(userId);
        }

        llmConfigMapper.insert(config);
        return ApiResponse.ok(config);
    }

    @PutMapping("/{id}")
    public ApiResponse<LlmConfig> update(HttpServletRequest request, @PathVariable Long id,
                                          @RequestBody LlmConfig update) {
        Long userId = getUserId(request);
        LlmConfig existing = getAndCheckOwner(id, userId);

        if (Boolean.TRUE.equals(existing.getIsSystem())) {
            throw new BusinessException("系统预设配置不可编辑");
        }

        if (update.getName() != null) existing.setName(update.getName());
        if (update.getProvider() != null) existing.setProvider(update.getProvider());
        if (update.getApiKey() != null && !update.getApiKey().contains("***")) {
            existing.setApiKey(update.getApiKey());
        }
        if (update.getModelName() != null) existing.setModelName(update.getModelName());
        if (update.getBaseUrl() != null) existing.setBaseUrl(update.getBaseUrl());
        if (update.getIsDefault() != null) {
            if (Boolean.TRUE.equals(update.getIsDefault())) {
                clearDefaultFlag(userId);
            }
            existing.setIsDefault(update.getIsDefault());
        }
        if (update.getMultimodal() != null) existing.setMultimodal(update.getMultimodal());
        if (update.getSecretKey() != null && !update.getSecretKey().contains("***")) {
            existing.setSecretKey(update.getSecretKey());
        }

        llmConfigMapper.updateById(existing);
        return ApiResponse.ok(existing);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(HttpServletRequest request, @PathVariable Long id) {
        LlmConfig config = getAndCheckOwner(id, getUserId(request));
        if (Boolean.TRUE.equals(config.getIsSystem())) {
            throw new BusinessException("系统预设配置不可删除");
        }
        llmConfigMapper.deleteById(id);
        return ApiResponse.ok();
    }

    private LlmConfig getAndCheckOwner(Long id, Long userId) {
        LlmConfig config = llmConfigMapper.selectById(id);
        if (config == null) throw new BusinessException("配置不存在");
        if (Boolean.TRUE.equals(config.getIsSystem())) return config;
        if (config.getUserId() == null || !config.getUserId().equals(userId)) {
            throw new BusinessException("无权访问此配置");
        }
        return config;
    }

    private void clearDefaultFlag(Long userId) {
        List<LlmConfig> defaults = llmConfigMapper.selectList(
                new LambdaQueryWrapper<LlmConfig>()
                        .eq(LlmConfig::getUserId, userId)
                        .eq(LlmConfig::getIsDefault, true));
        for (LlmConfig c : defaults) {
            c.setIsDefault(false);
            llmConfigMapper.updateById(c);
        }
    }

    private String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.length() <= 8) return "***";
        return apiKey.substring(0, 4) + "***" + apiKey.substring(apiKey.length() - 4);
    }

    private Long getUserId(HttpServletRequest request) {
        return (Long) request.getAttribute(AuthInterceptor.CURRENT_USER_ID);
    }
}
