package com.hznan.mamgareader.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hznan.mamgareader.exception.BusinessException;
import com.hznan.mamgareader.model.entity.TranslateConfig;
import com.hznan.mamgareader.mapper.TranslateConfigMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ConfigService {

    private final TranslateConfigMapper configMapper;

    public List<TranslateConfig> getUserConfigs(Long userId) {
        return configMapper.selectList(
                new LambdaQueryWrapper<TranslateConfig>()
                        .eq(TranslateConfig::getUserId, userId)
                        .orderByDesc(TranslateConfig::getCreatedAt));
    }

    public List<TranslateConfig> getPresets() {
        return configMapper.selectList(
                new LambdaQueryWrapper<TranslateConfig>()
                        .eq(TranslateConfig::getUserId, 0L)
                        .orderByAsc(TranslateConfig::getId));
    }

    @Transactional
    public TranslateConfig create(Long userId, TranslateConfig config) {
        config.setId(null);
        config.setUserId(userId);
        configMapper.insert(config);
        return config;
    }

    @Transactional
    public TranslateConfig update(Long id, Long userId, TranslateConfig updates) {
        TranslateConfig config = configMapper.selectById(id);
        if (config == null) throw new BusinessException("配置不存在");
        if (!config.getUserId().equals(userId)) {
            throw new BusinessException("无权修改此配置");
        }

        if (updates.getName() != null) config.setName(updates.getName());
        if (updates.getIsDefault() != null) config.setIsDefault(updates.getIsDefault());
        if (updates.getTargetLang() != null) config.setTargetLang(updates.getTargetLang());
        if (updates.getTranslator() != null) config.setTranslator(updates.getTranslator());
        if (updates.getDetector() != null) config.setDetector(updates.getDetector());
        if (updates.getDetectionSize() != null) config.setDetectionSize(updates.getDetectionSize());
        if (updates.getTextThreshold() != null) config.setTextThreshold(updates.getTextThreshold());
        if (updates.getBoxThreshold() != null) config.setBoxThreshold(updates.getBoxThreshold());
        if (updates.getUnclipRatio() != null) config.setUnclipRatio(updates.getUnclipRatio());
        if (updates.getOcr() != null) config.setOcr(updates.getOcr());
        if (updates.getSourceLang() != null) config.setSourceLang(updates.getSourceLang());
        if (updates.getUseMocrMerge() != null) config.setUseMocrMerge(updates.getUseMocrMerge());
        if (updates.getInpainter() != null) config.setInpainter(updates.getInpainter());
        if (updates.getInpaintingSize() != null) config.setInpaintingSize(updates.getInpaintingSize());
        if (updates.getInpaintingPrecision() != null) config.setInpaintingPrecision(updates.getInpaintingPrecision());
        if (updates.getRenderer() != null) config.setRenderer(updates.getRenderer());
        if (updates.getAlignment() != null) config.setAlignment(updates.getAlignment());
        if (updates.getDirection() != null) config.setDirection(updates.getDirection());
        if (updates.getFontSizeOffset() != null) config.setFontSizeOffset(updates.getFontSizeOffset());
        if (updates.getMaskDilationOffset() != null) config.setMaskDilationOffset(updates.getMaskDilationOffset());
        if (updates.getKernelSize() != null) config.setKernelSize(updates.getKernelSize());
        if (updates.getUpscaler() != null) config.setUpscaler(updates.getUpscaler());
        if (updates.getUpscaleRatio() != null) config.setUpscaleRatio(updates.getUpscaleRatio());
        if (updates.getColorizer() != null) config.setColorizer(updates.getColorizer());
        config.setLlmConfigId(updates.getLlmConfigId());
        if (updates.getExtraConfig() != null) config.setExtraConfig(updates.getExtraConfig());

        configMapper.updateById(config);
        return config;
    }

    @Transactional
    public void delete(Long id, Long userId) {
        TranslateConfig config = configMapper.selectById(id);
        if (config == null) throw new BusinessException("配置不存在");
        if (!config.getUserId().equals(userId)) {
            throw new BusinessException("无权删除此配置");
        }
        configMapper.deleteById(id);
    }
}
