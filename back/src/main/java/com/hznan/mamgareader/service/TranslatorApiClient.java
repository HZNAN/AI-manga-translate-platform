package com.hznan.mamgareader.service;

import tools.jackson.databind.ObjectMapper;
import com.hznan.mamgareader.model.entity.TranslateConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class TranslatorApiClient {

    private final RestClient translatorRestClient;
    private final ObjectMapper objectMapper;

    /**
     * 调用翻译 API 获取翻译后的图片
     */
    public byte[] translateImage(byte[] imageData, String filename, TranslateConfig config) {
        try {
            MultiValueMap<String, Object> body = buildMultipartBody(imageData, filename, config);

            return translatorRestClient.post()
                    .uri("/translate/with-form/image")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .body(byte[].class);
        } catch (Exception e) {
            log.error("翻译 API 调用失败", e);
            throw new RuntimeException("翻译 API 调用失败: " + e.getMessage(), e);
        }
    }

    /**
     * 调用翻译 API 获取 JSON 结构化结果
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> translateJson(byte[] imageData, String filename, TranslateConfig config) {
        try {
            MultiValueMap<String, Object> body = buildMultipartBody(imageData, filename, config);

            return translatorRestClient.post()
                    .uri("/translate/with-form/json")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .body(Map.class);
        } catch (Exception e) {
            log.error("翻译 JSON API 调用失败", e);
            throw new RuntimeException("翻译 API 调用失败: " + e.getMessage(), e);
        }
    }

    /**
     * 调用翻译 API 进行纯 inpaint（不翻译、不渲染），返回抠字后图片。
     * 使用提供的配置中的检测器/修复器参数，仅覆盖 translator 和 renderer 为 none。
     */
    public byte[] inpaintImage(byte[] imageData, String filename, TranslateConfig baseConfig) {
        try {
            TranslateConfig inpaintConfig = new TranslateConfig();
            if (baseConfig != null) {
                inpaintConfig.setDetector(baseConfig.getDetector());
                inpaintConfig.setDetectionSize(baseConfig.getDetectionSize());
                inpaintConfig.setTextThreshold(baseConfig.getTextThreshold());
                inpaintConfig.setBoxThreshold(baseConfig.getBoxThreshold());
                inpaintConfig.setUnclipRatio(baseConfig.getUnclipRatio());
                inpaintConfig.setInpainter(baseConfig.getInpainter());
                inpaintConfig.setInpaintingSize(baseConfig.getInpaintingSize());
                inpaintConfig.setInpaintingPrecision(baseConfig.getInpaintingPrecision());
                inpaintConfig.setKernelSize(baseConfig.getKernelSize());
                inpaintConfig.setMaskDilationOffset(baseConfig.getMaskDilationOffset());
            }
            inpaintConfig.setTranslator("none");
            inpaintConfig.setRenderer("none");

            MultiValueMap<String, Object> body = buildMultipartBody(imageData, filename, inpaintConfig);

            return translatorRestClient.post()
                    .uri("/translate/with-form/image")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .body(byte[].class);
        } catch (Exception e) {
            log.error("Inpaint API 调用失败", e);
            throw new RuntimeException("Inpaint API 调用失败: " + e.getMessage(), e);
        }
    }

    /**
     * 调用翻译 API 获取 OCR 结果（JSON 模式），继承 baseConfig 的检测器设置
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> ocrImage(byte[] imageData, String filename, TranslateConfig baseConfig) {
        try {
            TranslateConfig ocrConfig = new TranslateConfig();
            if (baseConfig != null) {
                ocrConfig.setDetector(baseConfig.getDetector());
                ocrConfig.setDetectionSize(baseConfig.getDetectionSize());
                ocrConfig.setOcr(baseConfig.getOcr());
                ocrConfig.setSourceLang(baseConfig.getSourceLang());
            }
            ocrConfig.setTranslator("none");
            ocrConfig.setRenderer("none");
            ocrConfig.setInpainter("none");

            MultiValueMap<String, Object> body = buildMultipartBody(imageData, filename, ocrConfig);

            return translatorRestClient.post()
                    .uri("/translate/with-form/json")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .body(Map.class);
        } catch (Exception e) {
            log.error("OCR API 调用失败", e);
            throw new RuntimeException("OCR API 调用失败: " + e.getMessage(), e);
        }
    }

    // ==================== Step APIs (解耦管线) ====================

    /**
     * 步骤 1: Detection + OCR。直接调用 Python core 函数，不走 executor。
     * 返回 bubble 坐标、原文、方向、颜色、raw_mask 等。
     */
    @SuppressWarnings("unchecked")
    public DetectOcrResult detectAndOcr(byte[] imageData, String filename, TranslateConfig config) {
        try {
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("image", new ByteArrayResource(imageData) {
                @Override public String getFilename() { return filename; }
            });
            body.add("config", objectMapper.writeValueAsString(buildConfigMap(config)));

            Map<String, Object> resp = translatorRestClient.post()
                    .uri("/step/detect-and-ocr")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .body(Map.class);

            if (resp == null) throw new RuntimeException("detect-and-ocr 返回 null");
            return DetectOcrResult.fromMap(resp);
        } catch (Exception e) {
            log.error("detect-and-ocr 步骤调用失败", e);
            throw new RuntimeException("detect-and-ocr 步骤失败: " + e.getMessage(), e);
        }
    }

    /**
     * 步骤 2: Inpainting。用原图 + bubble 坐标 + raw_mask 进行修复。
     * 返回 clean image (PNG bytes)。
     */
    public byte[] inpaintStep(byte[] imageData, String filename,
                              String rawMaskBase64, String bubbleCoordsJson,
                              TranslateConfig config) {
        try {
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("image", new ByteArrayResource(imageData) {
                @Override public String getFilename() { return filename; }
            });
            body.add("bubble_coords", bubbleCoordsJson);
            body.add("raw_mask", rawMaskBase64 != null ? rawMaskBase64 : "");
            body.add("config", objectMapper.writeValueAsString(buildConfigMap(config)));

            return translatorRestClient.post()
                    .uri("/step/inpaint")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .body(byte[].class);
        } catch (Exception e) {
            log.error("inpaint 步骤调用失败", e);
            throw new RuntimeException("inpaint 步骤失败: " + e.getMessage(), e);
        }
    }

    /**
     * 步骤 3: Rendering。在 clean image 上渲染翻译文本。
     * translationsJson: 按 text_region 顺序排列的翻译字符串数组 JSON，如 ["译文1", "译文2"]
     * textRegionsDataJson: 从 detect-and-ocr 步骤返回的原生 TextBlock 序列化数据
     * 返回最终 PNG bytes。
     */
    public byte[] renderStep(byte[] cleanImage, String filename,
                             String translationsJson, String textRegionsDataJson,
                             TranslateConfig config) {
        try {
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("image", new ByteArrayResource(cleanImage) {
                @Override public String getFilename() { return filename; }
            });
            body.add("translations", translationsJson);
            body.add("text_regions_data", textRegionsDataJson != null ? textRegionsDataJson : "[]");
            body.add("config", objectMapper.writeValueAsString(buildConfigMap(config)));

            return translatorRestClient.post()
                    .uri("/step/render")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .body(byte[].class);
        } catch (Exception e) {
            log.error("render 步骤调用失败", e);
            throw new RuntimeException("render 步骤失败: " + e.getMessage(), e);
        }
    }

    /**
     * Detection + OCR 步骤的返回结果
     */
    public record DetectOcrResult(
            List<int[]> bubbleCoords,
            List<String> originalTexts,
            List<String> autoDirections,
            List<Integer> fontSizes,
            List<int[]> fgColors,
            List<int[]> bgColors,
            String rawMaskBase64,
            String maskBase64,
            int regionCount,
            String textRegionsDataJson
    ) {
        @SuppressWarnings("unchecked")
        public static DetectOcrResult fromMap(Map<String, Object> map) {
            List<List<Integer>> coordsRaw = (List<List<Integer>>) map.get("bubble_coords");
            List<int[]> coords = coordsRaw.stream()
                    .map(c -> c.stream().mapToInt(Integer::intValue).toArray())
                    .toList();

            List<String> texts = (List<String>) map.get("original_texts");
            List<String> dirs = (List<String>) map.get("auto_directions");
            List<Integer> fsList = ((List<Number>) map.get("font_sizes")).stream()
                    .map(Number::intValue).toList();

            List<List<Integer>> fgRaw = (List<List<Integer>>) map.get("fg_colors");
            List<int[]> fgColors = fgRaw.stream()
                    .map(c -> c.stream().mapToInt(Integer::intValue).toArray())
                    .toList();

            List<List<Integer>> bgRaw = (List<List<Integer>>) map.get("bg_colors");
            List<int[]> bgColors = bgRaw.stream()
                    .map(c -> c.stream().mapToInt(Integer::intValue).toArray())
                    .toList();

            String rawMask = (String) map.get("raw_mask");
            String mask = (String) map.get("mask");
            int count = ((Number) map.get("region_count")).intValue();

            String textRegionsJson = "";
            Object trdObj = map.get("text_regions_data");
            if (trdObj instanceof List<?>) {
                try {
                    textRegionsJson = new tools.jackson.databind.ObjectMapper()
                            .writeValueAsString(trdObj);
                } catch (Exception e) {
                    textRegionsJson = "[]";
                }
            }

            return new DetectOcrResult(coords, texts, dirs, fsList, fgColors, bgColors,
                    rawMask, mask, count, textRegionsJson);
        }
    }

    /**
     * 查询翻译引擎队列长度
     */
    public int getQueueSize() {
        try {
            Integer size = translatorRestClient.post()
                    .uri("/queue-size")
                    .retrieve()
                    .body(Integer.class);
            return size != null ? size : 0;
        } catch (Exception e) {
            log.warn("查询队列长度失败", e);
            return -1;
        }
    }

    private MultiValueMap<String, Object> buildMultipartBody(byte[] imageData, String filename, TranslateConfig config) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();

        body.add("image", new ByteArrayResource(imageData) {
            @Override
            public String getFilename() {
                return filename;
            }
        });

        try {
            String configJson = objectMapper.writeValueAsString(buildConfigMap(config));
            body.add("config", configJson);
        } catch (Exception e) {
            log.warn("序列化翻译配置失败, 使用空配置", e);
            body.add("config", "{}");
        }

        return body;
    }

    private Map<String, Object> buildConfigMap(TranslateConfig config) {
        if (config == null) {
            return Map.of();
        }

        Map<String, Object> root = new LinkedHashMap<>();

        Map<String, Object> translator = new LinkedHashMap<>();
        translator.put("translator", config.getTranslator());
        translator.put("target_lang", config.getTargetLang());
        root.put("translator", translator);

        Map<String, Object> detector = new LinkedHashMap<>();
        detector.put("detector", config.getDetector());
        detector.put("detection_size", config.getDetectionSize());
        detector.put("text_threshold", toDouble(config.getTextThreshold()));
        detector.put("box_threshold", toDouble(config.getBoxThreshold()));
        detector.put("unclip_ratio", toDouble(config.getUnclipRatio()));
        root.put("detector", detector);

        Map<String, Object> ocr = new LinkedHashMap<>();
        ocr.put("ocr", config.getOcr());
        if (config.getSourceLang() != null) {
            ocr.put("source_lang", config.getSourceLang());
        }
        ocr.put("use_mocr_merge", Boolean.TRUE.equals(config.getUseMocrMerge()));
        root.put("ocr", ocr);

        Map<String, Object> inpainter = new LinkedHashMap<>();
        inpainter.put("inpainter", config.getInpainter());
        inpainter.put("inpainting_size", config.getInpaintingSize());
        inpainter.put("inpainting_precision", config.getInpaintingPrecision());
        root.put("inpainter", inpainter);

        Map<String, Object> render = new LinkedHashMap<>();
        render.put("renderer", config.getRenderer());
        render.put("alignment", config.getAlignment());
        render.put("direction", config.getDirection());
        render.put("font_size_offset", config.getFontSizeOffset());
        root.put("render", render);

        root.put("kernel_size", config.getKernelSize());
        root.put("mask_dilation_offset", config.getMaskDilationOffset());

        if (config.getUpscaler() != null) {
            Map<String, Object> upscale = new LinkedHashMap<>();
            upscale.put("upscaler", config.getUpscaler());
            if (config.getUpscaleRatio() != null) {
                upscale.put("upscale_ratio", config.getUpscaleRatio());
                upscale.put("revert_upscaling", true);
            }
            root.put("upscale", upscale);
        }

        if (config.getColorizer() != null && !"none".equals(config.getColorizer())) {
            root.put("colorizer", Map.of("colorizer", config.getColorizer()));
        }

        if (config.getExtraConfig() != null) {
            root.putAll(config.getExtraConfig());
        }

        return root;
    }

    private double toDouble(BigDecimal bd) {
        return bd != null ? bd.doubleValue() : 0;
    }
}
