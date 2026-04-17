package com.hznan.mamgareader.mq;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hznan.mamgareader.config.RabbitMQConfig;
import com.hznan.mamgareader.mapper.*;
import com.hznan.mamgareader.model.entity.*;
import com.hznan.mamgareader.service.FileStorageService;
import com.hznan.mamgareader.service.pipeline.AbstractTranslationPipeline;
import com.hznan.mamgareader.service.pipeline.TranslationContext;
import com.hznan.mamgareader.service.pipeline.TranslationPipelineFactory;
import com.hznan.mamgareader.websocket.TranslateProgressNotifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * LLM 翻译消息消费者 — 监听 LLM 翻译队列，委派给翻译管线执行。
 * <p>
 * 职责明确：消费者只负责「加载数据 → 构建上下文 → 调用管线 → 保存结果」，
 * 具体的翻译管线流程由 {@link AbstractTranslationPipeline} 模板方法定义，
 * 具体翻译步骤由 {@link TranslationPipelineFactory} 选择的管线子类实现。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LlmTranslateConsumer {

    private final TranslationTaskMapper taskMapper;
    private final TranslationRecordMapper recordMapper;
    private final MangaPageMapper mangaPageMapper;
    private final MangaMapper mangaMapper;
    private final LlmConfigMapper llmConfigMapper;
    private final FileStorageService fileStorageService;
    private final ObjectMapper objectMapper;
    private final TranslateProgressNotifier progressNotifier;
    private final TranslationPipelineFactory pipelineFactory;

    @RabbitListener(queues = RabbitMQConfig.LLM_QUEUE, concurrency = "2-4")
    public void handleLlmTranslateMessage(LlmTranslateMessage message) {
        Long recordId = message.getRecordId();
        Long taskId = message.getTaskId();
        log.info("收到 LLM 翻译消息, recordId={}, taskId={}", recordId, taskId);

        try {
            doHandleSinglePage(message);
        } catch (Exception e) {
            log.error("LLM 翻译异常, recordId={}, taskId={}", recordId, taskId, e);
            failRecordById(recordId, "翻译异常: " + e.getMessage());
        } finally {
            updateTaskProgress(taskId);
        }
    }

    private void doHandleSinglePage(LlmTranslateMessage message) {
        Long recordId = message.getRecordId();
        Long taskId = message.getTaskId();

        // ─── 1. 加载并校验记录状态 ──────────────────────────────────
        TranslationRecord record = recordMapper.selectById(recordId);
        if (record == null) {
            log.warn("翻译记录不存在, recordId={}", recordId);
            return;
        }

        if (!"queued".equals(record.getStatus())) {
            log.info("记录状态非 queued，跳过, recordId={}, status={}", recordId, record.getStatus());
            return;
        }

        if (taskId != null) {
            TranslationTask task = taskMapper.selectById(taskId);
            if (task != null && "cancelled".equals(task.getStatus())) {
                log.info("任务已取消，跳过, recordId={}, taskId={}", recordId, taskId);
                record.setStatus("failed");
                record.setErrorMessage("任务已取消");
                record.setCompletedAt(LocalDateTime.now());
                recordMapper.updateById(record);
                return;
            }
        }

        record.setStatus("translating");
        recordMapper.updateById(record);

        // ─── 2. 加载配置和数据 ──────────────────────────────────────
        TranslateConfig translateConfig = restoreConfig(record.getConfigSnapshot());

        Long llmConfigId = message.getLlmConfigId();
        if (llmConfigId == null && translateConfig != null) {
            llmConfigId = translateConfig.getLlmConfigId();
        }

        LlmConfig llmConfig = resolveLlmConfig(llmConfigId);
        if (llmConfig == null) {
            failRecordById(recordId, "未找到可用的 LLM 配置");
            return;
        }

        MangaPage page = mangaPageMapper.selectById(record.getPageId());
        if (page == null) {
            failRecordById(recordId, "页面不存在");
            return;
        }

        Manga manga = mangaMapper.selectById(record.getMangaId());
        if (manga == null) {
            failRecordById(recordId, "漫画不存在");
            return;
        }

        byte[] imageData;
        try {
            imageData = fileStorageService.readFile(page.getImagePath());
        } catch (Exception e) {
            failRecordById(recordId, "读取原图失败: " + e.getMessage());
            return;
        }
        if (imageData == null) {
            failRecordById(recordId, "原图文件不存在");
            return;
        }

        String targetLang = translateConfig != null && translateConfig.getTargetLang() != null
                ? translateConfig.getTargetLang() : "CHS";

        long startMs = System.currentTimeMillis();

        // ─── 3. 构建上下文 → 选择管线 → 执行 ────────────────────────
        TranslationContext ctx = TranslationContext.builder()
                .imageData(imageData)
                .filename(page.getOriginalFilename())
                .translateConfig(translateConfig)
                .llmConfig(llmConfig)
                .targetLang(targetLang)
                .build();

        AbstractTranslationPipeline pipeline = pipelineFactory.getPipeline(llmConfig.getProvider());
        byte[] finalImage;

        try {
            finalImage = pipeline.execute(ctx);
        } catch (Exception e) {
            log.error("管线执行失败: recordId={}", recordId, e);
            failRecordById(recordId, "翻译失败: " + e.getMessage());
            return;
        }

        // 管线返回 null 表示未检测到文本，使用原图
        if (finalImage == null) {
            log.info("未检测到文本区域，直接使用原图: recordId={}", recordId);
            completeWithOriginalImage(record, page, manga, imageData, startMs);
            return;
        }

        // ─── 4. 保存结果 ────────────────────────────────────────────
        try {
            String mangaDir = fileStorageService.getMangaDir(record.getUserId(), manga.getId());
            String filename = "llm_page_" + page.getPageNumber() + "_" + startMs + ".png";
            String translatedPath = fileStorageService.storeFile(
                    mangaDir + "/translated", filename, finalImage);

            page.setIsTranslated(true);
            page.setTranslatedImagePath(translatedPath);
            mangaPageMapper.updateById(page);

            record = recordMapper.selectById(recordId);
            if (record != null) {
                record.setStatus("machine_completed");
                record.setTranslatedImagePath(translatedPath);
                record.setDurationMs((int) (System.currentTimeMillis() - startMs));
                record.setCompletedAt(LocalDateTime.now());
                recordMapper.updateById(record);
            }

            progressNotifier.notifyRecordStatus(record.getUserId(), recordId,
                    record.getPageId(), "machine_completed", null);
            log.info("LLM 翻译完成: recordId={}, pageId={}, 耗时={}ms",
                    recordId, page.getId(), System.currentTimeMillis() - startMs);
        } catch (Exception e) {
            log.error("保存结果失败: recordId={}", recordId, e);
            failRecordById(recordId, "保存失败: " + e.getMessage());
        }
    }

    // ─── 辅助方法 ───────────────────────────────────────────────────

    private LlmConfig resolveLlmConfig(Long llmConfigId) {
        LlmConfig llmConfig = null;
        if (llmConfigId != null) {
            llmConfig = llmConfigMapper.selectById(llmConfigId);
        }
        if (llmConfig == null) {
            llmConfig = llmConfigMapper.selectOne(
                    new LambdaQueryWrapper<LlmConfig>()
                            .eq(LlmConfig::getIsSystem, true)
                            .eq(LlmConfig::getIsDefault, true)
                            .last("LIMIT 1"));
        }
        if (llmConfig == null) {
            llmConfig = llmConfigMapper.selectOne(
                    new LambdaQueryWrapper<LlmConfig>()
                            .eq(LlmConfig::getIsSystem, true)
                            .last("LIMIT 1"));
        }
        return llmConfig;
    }

    private void completeWithOriginalImage(TranslationRecord record, MangaPage page,
                                              Manga manga, byte[] imageData, long startMs) {
        try {
            String mangaDir = fileStorageService.getMangaDir(record.getUserId(), manga.getId());
            String filename = "llm_page_" + page.getPageNumber() + "_" + startMs + ".png";
            String translatedPath = fileStorageService.storeFile(
                    mangaDir + "/translated", filename, imageData);

            page.setIsTranslated(true);
            page.setTranslatedImagePath(translatedPath);
            mangaPageMapper.updateById(page);

            record.setStatus("machine_completed");
            record.setTranslatedImagePath(translatedPath);
            record.setDurationMs((int) (System.currentTimeMillis() - startMs));
            record.setCompletedAt(LocalDateTime.now());
            recordMapper.updateById(record);

            progressNotifier.notifyRecordStatus(record.getUserId(), record.getId(),
                    record.getPageId(), "machine_completed", null);
            log.info("无文本区域，原图作为译图完成: recordId={}, pageId={}", record.getId(), page.getId());
        } catch (Exception e) {
            log.error("保存原图为译图失败: recordId={}", record.getId(), e);
            failRecordById(record.getId(), "保存失败: " + e.getMessage());
        }
    }

    private void failRecordById(Long recordId, String errorMessage) {
        if (recordId == null) return;
        try {
            TranslationRecord record = recordMapper.selectById(recordId);
            if (record != null && !"machine_completed".equals(record.getStatus())) {
                record.setStatus("failed");
                record.setErrorMessage(errorMessage);
                record.setCompletedAt(LocalDateTime.now());
                recordMapper.updateById(record);

                progressNotifier.notifyRecordStatus(record.getUserId(), recordId,
                        record.getPageId(), "failed", errorMessage);
            }
        } catch (Exception e) {
            log.error("更新记录失败状态异常, recordId={}", recordId, e);
        }
    }

    private void updateTaskProgress(Long taskId) {
        if (taskId == null) return;
        try {
            TranslationTask task = taskMapper.selectById(taskId);
            if (task == null || "cancelled".equals(task.getStatus())) return;

            var allRecords = recordMapper.selectList(
                    new LambdaQueryWrapper<TranslationRecord>()
                            .eq(TranslationRecord::getTaskId, taskId));

            int completed = 0, failed = 0;
            for (TranslationRecord r : allRecords) {
                if ("machine_completed".equals(r.getStatus())) completed++;
                else if ("failed".equals(r.getStatus())) failed++;
            }

            task.setCompletedPages(completed);
            task.setFailedPages(failed);

            if (completed + failed >= task.getTotalPages()) {
                task.setStatus("completed");
            } else {
                task.setStatus("processing");
            }
            taskMapper.updateById(task);

            progressNotifier.notifyTaskProgress(task.getUserId(), taskId,
                    task.getStatus(), completed, failed, task.getTotalPages());
        } catch (Exception e) {
            log.error("更新任务进度异常, taskId={}", taskId, e);
        }
    }

    private TranslateConfig restoreConfig(Map<String, Object> snapshot) {
        if (snapshot == null || snapshot.isEmpty()) {
            return new TranslateConfig();
        }
        return objectMapper.convertValue(snapshot, TranslateConfig.class);
    }
}
