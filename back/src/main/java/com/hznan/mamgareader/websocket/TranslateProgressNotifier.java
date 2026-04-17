package com.hznan.mamgareader.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class TranslateProgressNotifier {

    private final TranslateProgressHandler progressHandler;
    private final ObjectMapper objectMapper;

    /**
     * 推送单页翻译状态变更
     */
    public void notifyRecordStatus(Long userId, Long recordId, Long pageId,
                                   String status, String errorMessage) {
        try {
            Map<String, Object> payload = Map.of(
                    "type", "RECORD_STATUS",
                    "recordId", recordId,
                    "pageId", pageId,
                    "status", status,
                    "errorMessage", errorMessage != null ? errorMessage : ""
            );
            progressHandler.sendToUser(userId, objectMapper.writeValueAsString(payload));
        } catch (Exception e) {
            log.warn("推送翻译记录状态失败: {}", e.getMessage());
        }
    }

    /**
     * 推送批量任务进度
     */
    public void notifyTaskProgress(Long userId, Long taskId, String status,
                                   int completedPages, int failedPages, int totalPages) {
        try {
            Map<String, Object> payload = Map.of(
                    "type", "TASK_PROGRESS",
                    "taskId", taskId,
                    "status", status,
                    "completedPages", completedPages,
                    "failedPages", failedPages,
                    "totalPages", totalPages
            );
            progressHandler.sendToUser(userId, objectMapper.writeValueAsString(payload));
        } catch (Exception e) {
            log.warn("推送任务进度失败: {}", e.getMessage());
        }
    }
}
