package com.hznan.mamgareader.mq;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LlmTranslateMessage {
    private Long recordId;
    private Long taskId;
    private Long llmConfigId;
}
