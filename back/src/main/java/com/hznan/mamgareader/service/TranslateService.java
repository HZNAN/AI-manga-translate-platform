package com.hznan.mamgareader.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.hznan.mamgareader.config.RabbitMQConfig;
import com.hznan.mamgareader.exception.BusinessException;
import com.hznan.mamgareader.model.dto.TranslatePageRequest;
import com.hznan.mamgareader.model.entity.*;
import com.hznan.mamgareader.mapper.*;
import com.hznan.mamgareader.mq.LlmTranslateMessage;
import com.hznan.mamgareader.mq.TranslateMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class TranslateService {

    private final MangaPageMapper mangaPageMapper;
    private final MangaMapper mangaMapper;
    private final TranslationRecordMapper recordMapper;
    private final TranslationTaskMapper taskMapper;
    private final TranslateConfigMapper configMapper;
    private final TranslatorApiClient translatorApiClient;
    private final FileStorageService fileStorageService;
    private final LlmConfigMapper llmConfigMapper;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final RedissonClient redissonClient;

    @Transactional
    public TranslationRecord translatePage(Long userId, TranslatePageRequest req) {
        RLock lock = redissonClient.getLock("lock:translate:page:" + req.pageId());
        try {
            if (!lock.tryLock(3, 10, TimeUnit.SECONDS)) {
                throw new BusinessException("该页面翻译请求正在处理中，请稍后重试");
            }

            long inProgress = recordMapper.selectCount(
                    new LambdaQueryWrapper<TranslationRecord>()
                            .eq(TranslationRecord::getPageId, req.pageId())
                            .in(TranslationRecord::getStatus, "queued", "translating"));
            if (inProgress > 0) {
                throw new BusinessException("该页面已有翻译任务在进行中");
            }

            return doTranslatePage(userId, req);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("翻译请求被中断");
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private TranslationRecord doTranslatePage(Long userId, TranslatePageRequest req) {
        MangaPage page = mangaPageMapper.selectById(req.pageId());
        if (page == null) throw new BusinessException("页面不存在");

        Manga manga = mangaMapper.selectById(page.getMangaId());
        if (manga == null) throw new BusinessException("漫画不存在");
        if (!manga.getUserId().equals(userId)) {
            throw new BusinessException("无权访问此漫画");
        }

        TranslateConfig config = loadActiveConfig(manga);
        ensureLlmConfig(config);

        Map<String, Object> configSnapshot = buildConfigSnapshot(config);

        TranslationRecord record = TranslationRecord.builder()
                .userId(userId)
                .mangaId(manga.getId())
                .pageId(req.pageId())
                .chapterId(page.getChapterId())
                .pageNumber(page.getPageNumber())
                .configId(config.getId())
                .configSnapshot(configSnapshot)
                .status("queued")
                .build();
        recordMapper.insert(record);

        final Long recordId = record.getId();
        boolean useLlm = config.getLlmConfigId() != null;

        if (useLlm) {
            TranslationTask task = TranslationTask.builder()
                    .userId(userId)
                    .mangaId(manga.getId())
                    .configId(config.getId())
                    .totalPages(1)
                    .status("processing")
                    .build();
            taskMapper.insert(task);
            record.setTaskId(task.getId());
            recordMapper.updateById(record);

            final Long taskId = task.getId();
            final Long llmId = config.getLlmConfigId();
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    rabbitTemplate.convertAndSend(
                            RabbitMQConfig.EXCHANGE,
                            RabbitMQConfig.LLM_ROUTING_KEY,
                            new LlmTranslateMessage(recordId, taskId, llmId));
                    log.info("LLM 翻译任务已入队, recordId={}, taskId={}, llmConfigId={}, pageId={}",
                            recordId, taskId, llmId, req.pageId());
                }
            });
        } else {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    rabbitTemplate.convertAndSend(
                            RabbitMQConfig.EXCHANGE,
                            RabbitMQConfig.ROUTING_KEY,
                            new TranslateMessage(recordId));
                    log.info("Python 翻译任务已入队, recordId={}, pageId={}", recordId, req.pageId());
                }
            });
        }

        return record;
    }

    @Transactional
    public TranslationRecord translatePageJson(Long userId, TranslatePageRequest req) {
        MangaPage page = mangaPageMapper.selectById(req.pageId());
        if (page == null) throw new BusinessException("页面不存在");

        Manga manga = mangaMapper.selectById(page.getMangaId());
        if (manga == null) throw new BusinessException("漫画不存在");
        if (!manga.getUserId().equals(userId)) {
            throw new BusinessException("无权访问此漫画");
        }

        TranslateConfig config = loadActiveConfig(manga);

        TranslationRecord record = TranslationRecord.builder()
                .userId(userId)
                .mangaId(manga.getId())
                .pageId(req.pageId())
                .chapterId(page.getChapterId())
                .pageNumber(page.getPageNumber())
                .configId(config.getId())
                .status("translating")
                .build();
        recordMapper.insert(record);

        try {
            long start = System.currentTimeMillis();

            byte[] imageData = fileStorageService.readFile(page.getImagePath());
            if (imageData == null) throw new BusinessException("原图文件不存在");

            Map<String, Object> jsonResult = translatorApiClient.translateJson(
                    imageData, page.getOriginalFilename(), config);

            long elapsed = System.currentTimeMillis() - start;

            record.setStatus("machine_completed");
            record.setTranslationJson(jsonResult);
            record.setDurationMs((int) elapsed);
            record.setCompletedAt(java.time.LocalDateTime.now());
            recordMapper.updateById(record);

        } catch (Exception e) {
            log.error("翻译页面JSON失败, pageId={}", req.pageId(), e);
            record.setStatus("failed");
            record.setErrorMessage(e.getMessage());
            record.setCompletedAt(java.time.LocalDateTime.now());
            recordMapper.updateById(record);
        }

        return record;
    }

    @Transactional
    public TranslationTask createBatchTask(Long userId, Long mangaId,
                                            List<Long> pageIds, Boolean forceRetranslate) {
        Manga manga = mangaMapper.selectById(mangaId);
        if (manga == null) throw new BusinessException("漫画不存在");
        if (!manga.getUserId().equals(userId)) {
            throw new BusinessException("无权访问此漫画");
        }

        TranslateConfig config = loadActiveConfig(manga);
        ensureLlmConfig(config);

        List<MangaPage> pages;
        if (pageIds != null && !pageIds.isEmpty()) {
            pages = mangaPageMapper.selectBatchIds(pageIds);
        } else {
            pages = mangaPageMapper.selectList(
                    new LambdaQueryWrapper<MangaPage>()
                            .eq(MangaPage::getMangaId, mangaId)
                            .orderByAsc(MangaPage::getPageNumber));
        }

        if (!Boolean.TRUE.equals(forceRetranslate)) {
            pages = pages.stream().filter(p -> !Boolean.TRUE.equals(p.getIsTranslated())).toList();
        }

        if (pages.isEmpty()) {
            throw new BusinessException("没有需要翻译的页面");
        }

        Map<String, Object> configSnapshot = buildConfigSnapshot(config);

        TranslationTask task = TranslationTask.builder()
                .userId(userId)
                .mangaId(mangaId)
                .configId(config.getId())
                .totalPages(pages.size())
                .status("processing")
                .build();
        taskMapper.insert(task);

        List<Long> recordIds = new ArrayList<>();
        for (MangaPage page : pages) {
            TranslationRecord record = TranslationRecord.builder()
                    .userId(userId)
                    .mangaId(mangaId)
                    .pageId(page.getId())
                    .chapterId(page.getChapterId())
                    .pageNumber(page.getPageNumber())
                    .configId(config.getId())
                    .taskId(task.getId())
                    .configSnapshot(configSnapshot)
                    .status("queued")
                    .build();
            recordMapper.insert(record);
            recordIds.add(record.getId());
        }

        boolean useLlm = config.getLlmConfigId() != null;
        final Long batchLlmId = config.getLlmConfigId();
        final Long finalTaskId = task.getId();

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                if (useLlm) {
                    for (Long rid : recordIds) {
                        rabbitTemplate.convertAndSend(
                                RabbitMQConfig.EXCHANGE,
                                RabbitMQConfig.LLM_ROUTING_KEY,
                                new LlmTranslateMessage(rid, finalTaskId, batchLlmId));
                    }
                    log.info("LLM 批量翻译任务已入队, taskId={}, llmConfigId={}, 页数={}",
                            finalTaskId, batchLlmId, recordIds.size());
                } else {
                    for (Long rid : recordIds) {
                        rabbitTemplate.convertAndSend(
                                RabbitMQConfig.EXCHANGE,
                                RabbitMQConfig.ROUTING_KEY,
                                new TranslateMessage(rid));
                    }
                    log.info("Python 批量翻译任务已入队, taskId={}, 页数={}", finalTaskId, recordIds.size());
                }
            }
        });

        return task;
    }

    /**
     * 从漫画的 activeConfigId 加载翻译配置。
     * 配置不存在或已被删除时抛出异常，引导用户重新设置。
     */
    private TranslateConfig loadActiveConfig(Manga manga) {
        if (manga.getActiveConfigId() == null) {
            throw new BusinessException("请先为该漫画设置翻译配置");
        }
        TranslateConfig config = configMapper.selectById(manga.getActiveConfigId());
        if (config == null) {
            manga.setActiveConfigId(null);
            mangaMapper.updateById(manga);
            throw new BusinessException("当前使用的翻译配置已被删除，请重新设置");
        }
        return config;
    }

    /**
     * 当 translator="none" 且未关联 LLM 配置时，自动关联默认系统 LLM 配置。
     * 这保证翻译请求总是走 LLM 路径，而不是发给 Python 一个无翻译器的请求。
     */
    private void ensureLlmConfig(TranslateConfig config) {
        if (config.getLlmConfigId() != null) return;

        boolean pythonTranslatorSet = config.getTranslator() != null
                && !"none".equalsIgnoreCase(config.getTranslator());
        if (pythonTranslatorSet) return;

        LlmConfig defaultLlm = llmConfigMapper.selectOne(
                new LambdaQueryWrapper<LlmConfig>()
                        .eq(LlmConfig::getIsSystem, true)
                        .eq(LlmConfig::getIsDefault, true)
                        .last("LIMIT 1"));

        if (defaultLlm == null) {
            defaultLlm = llmConfigMapper.selectOne(
                    new LambdaQueryWrapper<LlmConfig>()
                            .eq(LlmConfig::getIsSystem, true)
                            .last("LIMIT 1"));
        }
        if (defaultLlm != null) {
            config.setLlmConfigId(defaultLlm.getId());
            log.info("未设置翻译模型，自动使用系统 LLM 配置: {} ({})",
                    defaultLlm.getName(), defaultLlm.getModelName());
        } else {
            log.warn("未找到任何系统 LLM 配置，翻译将仅执行检测和修复");
        }
    }

    public List<TranslationTask> getTasks(Long userId, Long mangaId) {
        LambdaQueryWrapper<TranslationTask> wrapper = new LambdaQueryWrapper<TranslationTask>()
                .eq(TranslationTask::getUserId, userId)
                .eq(mangaId != null, TranslationTask::getMangaId, mangaId)
                .orderByDesc(TranslationTask::getCreatedAt);
        return taskMapper.selectList(wrapper);
    }

    public TranslationTask getTask(Long taskId, Long userId) {
        TranslationTask task = taskMapper.selectById(taskId);
        if (task == null) throw new BusinessException("任务不存在");
        if (!task.getUserId().equals(userId)) {
            throw new BusinessException("无权访问此任务");
        }
        return task;
    }

    @Transactional
    public void cancelTask(Long taskId, Long userId) {
        TranslationTask task = getTask(taskId, userId);
        if ("pending".equals(task.getStatus()) || "processing".equals(task.getStatus())) {
            task.setStatus("cancelled");
            taskMapper.updateById(task);

            recordMapper.update(null, new LambdaUpdateWrapper<TranslationRecord>()
                    .eq(TranslationRecord::getTaskId, taskId)
                    .in(TranslationRecord::getStatus, "queued", "translating")
                    .set(TranslationRecord::getStatus, "failed")
                    .set(TranslationRecord::getErrorMessage, "任务已取消")
                    .set(TranslationRecord::getCompletedAt, LocalDateTime.now()));

            long cancelled = recordMapper.selectCount(
                    new LambdaQueryWrapper<TranslationRecord>()
                            .eq(TranslationRecord::getTaskId, taskId)
                            .eq(TranslationRecord::getStatus, "failed")
                            .eq(TranslationRecord::getErrorMessage, "任务已取消"));
            log.info("任务已取消, taskId={}, 标记 {} 条记录为失败", taskId, cancelled);
        }
    }

    public TranslationRecord getRecord(Long id) {
        TranslationRecord record = recordMapper.selectById(id);
        if (record == null) throw new BusinessException("翻译记录不存在");
        return record;
    }

    public List<TranslationRecord> getRecords(Long userId, Long mangaId, Long chapterId, Long pageId) {
        LambdaQueryWrapper<TranslationRecord> wrapper = new LambdaQueryWrapper<>();
        if (mangaId != null) {
            wrapper.eq(TranslationRecord::getMangaId, mangaId);
        }
        if (chapterId != null) {
            wrapper.eq(TranslationRecord::getChapterId, chapterId);
        }
        if (pageId != null) {
            wrapper.eq(TranslationRecord::getPageId, pageId);
        }
        if (mangaId == null && chapterId == null && pageId == null) {
            wrapper.eq(TranslationRecord::getUserId, userId);
        }
        wrapper.orderByDesc(TranslationRecord::getCreatedAt);
        return recordMapper.selectList(wrapper);
    }

    @Transactional
    public void rollbackToRecord(Long recordId, Long userId) {
        TranslationRecord record = recordMapper.selectById(recordId);
        if (record == null) throw new BusinessException("翻译记录不存在");
        if (!record.getUserId().equals(userId)) {
            throw new BusinessException("无权操作此记录");
        }
        if (!"machine_completed".equals(record.getStatus()) && !"manual_corrected".equals(record.getStatus())) {
            throw new BusinessException("只能回退到已完成的版本");
        }
        if (record.getTranslatedImagePath() == null) {
            throw new BusinessException("该记录没有翻译图片");
        }

        MangaPage page = mangaPageMapper.selectById(record.getPageId());
        if (page == null) throw new BusinessException("页面不存在");

        page.setIsTranslated(true);
        page.setTranslatedImagePath(record.getTranslatedImagePath());
        mangaPageMapper.updateById(page);

        log.info("已回退到版本, recordId={}, pageId={}", recordId, record.getPageId());
    }

    public byte[] getRecordImage(Long id, Long userId) throws IOException {
        TranslationRecord record = recordMapper.selectById(id);
        if (record == null) throw new BusinessException("翻译记录不存在");
        if (!record.getUserId().equals(userId)) {
            throw new BusinessException("无权访问此记录");
        }
        return readRecordImage(record);
    }

    public byte[] getRecordImagePublic(Long id) throws IOException {
        TranslationRecord record = recordMapper.selectById(id);
        if (record == null) throw new BusinessException("翻译记录不存在");
        return readRecordImage(record);
    }

    public byte[] getTranslatedPageImage(Long pageId) throws IOException {
        MangaPage page = mangaPageMapper.selectById(pageId);
        if (page == null) throw new BusinessException("页面不存在");
        if (page.getTranslatedImagePath() == null) {
            throw new BusinessException("该页面尚未翻译");
        }
        byte[] data = fileStorageService.readFile(page.getTranslatedImagePath());
        if (data == null) throw new BusinessException("翻译图片文件不存在");
        return data;
    }

    private byte[] readRecordImage(TranslationRecord record) throws IOException {
        if (record.getTranslatedImagePath() == null) {
            throw new BusinessException("翻译结果图片不存在");
        }
        byte[] data = fileStorageService.readFile(record.getTranslatedImagePath());
        if (data == null) throw new BusinessException("翻译结果图片文件不存在");
        return data;
    }

    public int getQueueSize() {
        return translatorApiClient.getQueueSize();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> buildConfigSnapshot(TranslateConfig config) {
        if (config == null) return Map.of();
        return objectMapper.convertValue(config, Map.class);
    }
}
