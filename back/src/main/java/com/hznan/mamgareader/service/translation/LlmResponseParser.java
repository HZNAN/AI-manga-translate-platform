package com.hznan.mamgareader.service.translation;

import com.hznan.mamgareader.service.translation.TranslationDTOs.*;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LLM 响应的多格式容错解析器。
 * <p>
 * 优先解析 {@code <|n|>} 编号格式（参考 Saber-Translator），
 * 若未检测到编号格式则回退到 JSON 多策略解析。
 * </p>
 *
 * <h3>解析策略（按优先级）</h3>
 * <ol>
 *   <li>{@code <|n|>} 编号格式 — 主策略，解析简单、位置精确</li>
 *   <li>JSON 直接解析</li>
 *   <li>JSON 修复未转义引号后解析</li>
 *   <li>JSON 子串提取后解析</li>
 *   <li>JSON 子串 + 修复引号后解析</li>
 * </ol>
 */
@Slf4j
public final class LlmResponseParser {

    private LlmResponseParser() {}

    private static final Pattern NUMBERED_MARKER = Pattern.compile("<\\|\\d+\\|>");

    /**
     * 解析 LLM 原始输出为结构化翻译结果。
     *
     * @param rawContent    LLM 返回的原始字符串
     * @param mapper        Jackson ObjectMapper
     * @param expectedCount 期望的翻译条目数（用于 {@code <|n|>} 格式的数量校验和填充）
     * @return 解析后的翻译结果列表
     * @throws RuntimeException 当所有解析策略均失败时
     */
    public static List<TranslatedPage> parse(String rawContent, ObjectMapper mapper, int expectedCount) {
        String content = clean(rawContent);

        // 策略 1: <|n|> 编号格式（主策略）
        try {
            List<TranslatedPage> result = parseNumberedFormat(content, expectedCount);
            if (result != null) {
                log.info("<|n|> 编号格式解析成功");
                return result;
            }
        } catch (Exception e) {
            log.warn("<|n|> 格式解析失败: {}", e.getMessage());
        }

        // 策略 2-5: JSON 回退（兼容不遵守格式指令的模型）
        return parseJsonFallback(content, mapper);
    }

    // ─── <|n|> 编号格式解析 ──────────────────────────────────────────

    private static List<TranslatedPage> parseNumberedFormat(String content, int expectedCount) {
        Matcher m = NUMBERED_MARKER.matcher(content);
        if (!m.find()) return null;

        // 收集所有 <|n|> 标记的位置
        List<int[]> markers = new ArrayList<>();
        m.reset();
        while (m.find()) {
            markers.add(new int[]{m.start(), m.end()});
        }

        if (markers.isEmpty()) return null;

        // 仅保留从第一个标记到最后一个标记的内容范围，裁剪前后解释性文字
        String[] allLines = content.split("\n");
        int firstMarkerLine = -1, lastMarkerLine = -1;
        for (int i = 0; i < allLines.length; i++) {
            if (NUMBERED_MARKER.matcher(allLines[i]).find()) {
                if (firstMarkerLine < 0) firstMarkerLine = i;
                lastMarkerLine = i;
            }
        }
        if (firstMarkerLine < 0) return null;

        StringBuilder trimmed = new StringBuilder();
        for (int i = firstMarkerLine; i <= lastMarkerLine; i++) {
            if (i > firstMarkerLine) trimmed.append('\n');
            trimmed.append(allLines[i]);
        }
        String trimmedContent = trimmed.toString();

        // 按 <|n|> 标记分割，提取翻译内容
        String[] rawParts = NUMBERED_MARKER.split(trimmedContent);
        List<String> translations = new ArrayList<>();
        for (int i = 1; i < rawParts.length; i++) {
            translations.add(rawParts[i].trim());
        }

        if (translations.isEmpty()) return null;

        log.info("<|n|> 格式: 期望 {} 条, 实际解析 {} 条", expectedCount, translations.size());

        if (translations.size() != expectedCount && expectedCount > 0) {
            log.warn("翻译数量不匹配: 期望 {}, 实际 {}", expectedCount, translations.size());
        }

        // 数量不足则填充空串，多余则截断
        if (expectedCount > 0) {
            while (translations.size() < expectedCount) {
                translations.add("");
            }
            if (translations.size() > expectedCount) {
                translations = new ArrayList<>(translations.subList(0, expectedCount));
            }
        }

        List<TranslatedBubble> bubbles = new ArrayList<>();
        for (int i = 0; i < translations.size(); i++) {
            bubbles.add(new TranslatedBubble(i + 1, "", translations.get(i)));
        }
        return List.of(new TranslatedPage(1, bubbles));
    }

    // ─── JSON 回退解析 ──────────────────────────────────────────────

    private static List<TranslatedPage> parseJsonFallback(String content, ObjectMapper mapper) {
        log.info("尝试 JSON 回退解析...");

        // 策略 2: 直接解析
        try {
            return tryParseJson(content, mapper);
        } catch (Exception e1) {
            log.warn("直接解析失败: {}", e1.getMessage());
        }

        // 策略 3: 修复未转义引号后解析
        String repaired = repairUnescapedQuotes(content);
        if (!repaired.equals(content)) {
            try {
                return tryParseJson(repaired, mapper);
            } catch (Exception e2) {
                log.warn("修复引号后解析失败: {}", e2.getMessage());
            }
        }

        // 策略 4: 提取 JSON 子串后解析
        String extracted = extractJsonSubstring(content);
        if (extracted != null && !extracted.equals(content)) {
            try {
                return tryParseJson(extracted, mapper);
            } catch (Exception e3) {
                log.warn("提取子串后解析失败: {}", e3.getMessage());

                // 策略 5: 提取子串 + 修复引号
                String extractedRepaired = repairUnescapedQuotes(extracted);
                if (!extractedRepaired.equals(extracted)) {
                    try {
                        return tryParseJson(extractedRepaired, mapper);
                    } catch (Exception e4) {
                        log.warn("提取子串+修复引号后仍失败: {}", e4.getMessage());
                    }
                }
            }
        }

        log.error("LLM 响应解析彻底失败，原始内容: {}", content);
        throw new RuntimeException("LLM 响应解析失败，无法从输出中提取有效翻译");
    }

    // ─── 清洗 ───────────────────────────────────────────────────────

    private static String clean(String content) {
        if (content == null) return "";
        content = content.trim();

        // 剥离 <think>...</think> 深度思考块
        int thinkEnd = content.indexOf("</think>");
        if (thinkEnd >= 0) content = content.substring(thinkEnd + 8).trim();

        // 剥离 markdown 代码块标记（兼容 ```json、```text 等）
        content = content.replaceAll("(?s)^```\\w*\\s*", "");
        content = content.replaceAll("(?s)\\s*```$", "");

        return content.trim();
    }

    // ─── JSON 多格式解析 ─────────────────────────────────────────────

    private static List<TranslatedPage> tryParseJson(String json, ObjectMapper mapper) throws Exception {
        JsonNode root = mapper.readTree(json);

        if (root.isObject() && root.has("images")) {
            return parseImagesArray(root.get("images"));
        }
        if (root.isArray()) {
            return parseImagesArray(root);
        }
        if (root.isObject() && root.has("bubbles")) {
            return List.of(parseSinglePage(root));
        }

        throw new RuntimeException("无法识别的 JSON 格式");
    }

    private static List<TranslatedPage> parseImagesArray(JsonNode imagesNode) {
        if (imagesNode == null || !imagesNode.isArray()) {
            throw new RuntimeException("images 不是数组");
        }
        List<TranslatedPage> result = new ArrayList<>();
        for (JsonNode node : imagesNode) {
            result.add(parseSinglePage(node));
        }
        return result;
    }

    private static TranslatedPage parseSinglePage(JsonNode node) {
        int imageIndex = node.has("imageIndex") ? node.get("imageIndex").asInt() : 1;
        List<TranslatedBubble> bubbles = new ArrayList<>();
        JsonNode bubblesNode = node.get("bubbles");
        if (bubblesNode != null && bubblesNode.isArray()) {
            for (JsonNode bNode : bubblesNode) {
                int bi = bNode.has("bubbleIndex") ? bNode.get("bubbleIndex").asInt() : bubbles.size() + 1;
                String original = textField(bNode, "original");
                String translated = textField(bNode, "translated");
                bubbles.add(new TranslatedBubble(bi, original, translated));
            }
        }
        bubbles.sort(Comparator.comparingInt(TranslatedBubble::bubbleIndex));
        return new TranslatedPage(imageIndex, bubbles);
    }

    private static String textField(JsonNode node, String field) {
        if (!node.has(field)) return "";
        JsonNode val = node.get(field);
        return val.isNull() ? "" : val.asString();
    }

    // ─── 修复字符串值中未转义的双引号 ─────────────────────────────────

    private static String repairUnescapedQuotes(String json) {
        if (json == null || json.isEmpty()) return json;

        StringBuilder sb = new StringBuilder(json.length() + 64);
        boolean inString = false;

        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);

            if (inString && c == '\\' && i + 1 < json.length()) {
                sb.append(c).append(json.charAt(i + 1));
                i++;
                continue;
            }

            if (c == '"') {
                if (!inString) {
                    inString = true;
                    sb.append(c);
                } else if (isStructuralClosingQuote(json, i)) {
                    inString = false;
                    sb.append(c);
                } else {
                    sb.append('\\').append('"');
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static boolean isStructuralClosingQuote(String json, int pos) {
        for (int j = pos + 1; j < json.length(); j++) {
            char next = json.charAt(j);
            if (next == ' ' || next == '\t' || next == '\n' || next == '\r') continue;
            return next == ',' || next == '}' || next == ']' || next == ':';
        }
        return true;
    }

    // ─── JSON 子串提取（花括号/方括号配对） ──────────────────────────

    private static String extractJsonSubstring(String content) {
        int objStart = content.indexOf('{');
        int arrStart = content.indexOf('[');

        int start;
        char open, close;
        if (objStart >= 0 && (arrStart < 0 || objStart < arrStart)) {
            start = objStart; open = '{'; close = '}';
        } else if (arrStart >= 0) {
            start = arrStart; open = '['; close = ']';
        } else {
            return null;
        }

        int depth = 0;
        boolean inString = false;
        for (int i = start; i < content.length(); i++) {
            char c = content.charAt(i);
            if (c == '"' && (i == 0 || content.charAt(i - 1) != '\\')) {
                inString = !inString;
            }
            if (!inString) {
                if (c == open) depth++;
                else if (c == close) {
                    depth--;
                    if (depth == 0) return content.substring(start, i + 1);
                }
            }
        }
        return content.substring(start);
    }
}
