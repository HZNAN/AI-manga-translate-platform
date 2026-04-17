package com.hznan.mamgareader.service;

import com.hznan.mamgareader.model.entity.LlmConfig;
import com.tencentcloudapi.common.Credential;
import com.tencentcloudapi.tmt.v20180321.TmtClient;
import com.tencentcloudapi.tmt.v20180321.models.TextTranslateRequest;
import com.tencentcloudapi.tmt.v20180321.models.TextTranslateResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class TencentTranslateClient {

    private static final String REGION = "ap-guangzhou";
    private static final long PROJECT_ID = 0L;

    public String translate(LlmConfig config, String sourceText, String sourceLang, String targetLang) {
        String secretId = config.getApiKey();
        String secretKey = config.getSecretKey();

        if (secretId == null || secretId.isBlank() || secretKey == null || secretKey.isBlank()) {
            throw new RuntimeException("腾讯翻译 SecretId/SecretKey 未配置");
        }

        try {
            Credential cred = new Credential(secretId, secretKey);
            TmtClient client = new TmtClient(cred, REGION);

            TextTranslateRequest req = new TextTranslateRequest();
            req.setSourceText(sourceText);
            req.setSource(mapLanguageCode(sourceLang));
            req.setTarget(mapLanguageCode(targetLang));
            req.setProjectId(PROJECT_ID);

            TextTranslateResponse resp = client.TextTranslate(req);
            return resp.getTargetText();
        } catch (Exception e) {
            log.error("腾讯翻译 API 调用失败: {}", e.getMessage(), e);
            throw new RuntimeException("腾讯翻译 API 调用失败: " + e.getMessage(), e);
        }
    }

    private String mapLanguageCode(String lang) {
        if (lang == null) return "auto";
        return switch (lang.toUpperCase()) {
            case "CHS", "CHT" -> "zh";
            case "JPN" -> "ja";
            case "ENG" -> "en";
            case "KOR" -> "ko";
            case "FRA" -> "fr";
            case "DEU" -> "de";
            case "RUS" -> "ru";
            default -> "auto";
        };
    }
}
