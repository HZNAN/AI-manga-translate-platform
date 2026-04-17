package com.hznan.mamgareader.mq;

import com.hznan.mamgareader.config.RabbitMQConfig;
import com.hznan.mamgareader.mapper.MangaMapper;
import com.hznan.mamgareader.mapper.MangaPageMapper;
import com.hznan.mamgareader.mapper.TranslationRecordMapper;
import com.hznan.mamgareader.mapper.TranslationTaskMapper;
import com.hznan.mamgareader.model.entity.*;
import com.hznan.mamgareader.service.FileStorageService;
import com.hznan.mamgareader.service.TranslatorApiClient;
import com.hznan.mamgareader.websocket.TranslateProgressNotifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class TranslateConsumer {

    private final TranslationRecordMapper recordMapper;
    private final TranslationTaskMapper taskMapper;
    private final MangaPageMapper mangaPageMapper;
    private final MangaMapper mangaMapper;
    private final TranslatorApiClient translatorApiClient;
    private final FileStorageService fileStorageService;
    private final ObjectMapper objectMapper;
    private final TranslateProgressNotifier progressNotifier;

    @RabbitListener(queues = RabbitMQConfig.QUEUE, concurrency = "2-4")
    public void handleTranslateMessage(TranslateMessage message) {
        Long recordId = message.getRecordId();
        log.info("收到翻译消息, recordId={}", recordId);

        TranslationRecord record = recordMapper.selectById(recordId);
        if (record == null) {
            log.warn("翻译记录不存在, recordId={}", recordId);
            return;
        }

        if (!"queued".equals(record.getStatus())) {
            log.warn("记录状态非 queued，跳过, recordId={}, status={}", recordId, record.getStatus());
            return;
        }

        if (record.getTaskId() != null) {
            TranslationTask task = taskMapper.selectById(record.getTaskId());
            if (task != null && "cancelled".equals(task.getStatus())) {
                log.info("任务已取消，跳过, recordId={}, taskId={}", recordId, record.getTaskId());
                record.setStatus("failed");
                record.setErrorMessage("任务已取消");
                record.setCompletedAt(LocalDateTime.now());
                recordMapper.updateById(record);
                updateTaskProgress(record.getTaskId());
                return;
            }
        }

        record.setStatus("translating");
        recordMapper.updateById(record);

        try {
            long start = System.currentTimeMillis();

            MangaPage page = mangaPageMapper.selectById(record.getPageId());
            if (page == null) throw new RuntimeException("页面不存在, pageId=" + record.getPageId());

            Manga manga = mangaMapper.selectById(record.getMangaId());
            if (manga == null) throw new RuntimeException("漫画不存在, mangaId=" + record.getMangaId());

            byte[] imageData = fileStorageService.readFile(page.getImagePath());
            if (imageData == null) throw new RuntimeException("原图文件不存在: " + page.getImagePath());

            TranslateConfig config = restoreConfig(record.getConfigSnapshot());

            byte[] translatedImage = translatorApiClient.translateImage(
                    imageData, page.getOriginalFilename(), config);

            String mangaDir = fileStorageService.getMangaDir(record.getUserId(), manga.getId());
            String translatedPath = fileStorageService.storeFile(
                    mangaDir + "/translated",
                    "page_" + page.getPageNumber() + "_" + System.currentTimeMillis() + ".png",
                    translatedImage);

            long elapsed = System.currentTimeMillis() - start;

            record.setStatus("machine_completed");
            record.setTranslatedImagePath(translatedPath);
            record.setDurationMs((int) elapsed);
            record.setCompletedAt(LocalDateTime.now());
            recordMapper.updateById(record);

            page.setIsTranslated(true);
            page.setTranslatedImagePath(translatedPath);
            mangaPageMapper.updateById(page);

            progressNotifier.notifyRecordStatus(record.getUserId(), recordId,
                    record.getPageId(), "machine_completed", null);
            log.info("翻译完成, recordId={}, 耗时={}ms", recordId, elapsed);

        } catch (Exception e) {
            log.error("翻译失败, recordId={}", recordId, e);
            record.setStatus("failed");
            record.setErrorMessage(e.getMessage());
            record.setCompletedAt(LocalDateTime.now());
            recordMapper.updateById(record);

            progressNotifier.notifyRecordStatus(record.getUserId(), recordId,
                    record.getPageId(), "failed", e.getMessage());
        }

        updateTaskProgress(record.getTaskId());
    }

    private void updateTaskProgress(Long taskId) {
        if (taskId == null) return;

        TranslationTask task = taskMapper.selectById(taskId);
        if (task == null || "cancelled".equals(task.getStatus())) return;

        var allRecords = recordMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<TranslationRecord>()
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
    }

    private TranslateConfig restoreConfig(Map<String, Object> snapshot) {
        if (snapshot == null || snapshot.isEmpty()) {
            return new TranslateConfig();
        }
        return objectMapper.convertValue(snapshot, TranslateConfig.class);
    }
}
