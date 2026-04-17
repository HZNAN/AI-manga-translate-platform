package com.hznan.mamgareader.service.translation;

import java.util.Map;

/**
 * 语言代码 ↔ 可读描述的映射。
 * <p>
 * 统一管理翻译系统中使用的语言代码（如 "CHS"、"JPN"），
 * 避免在多处重复 switch/if 映射逻辑。
 * </p>
 */
public final class LanguageMapping {

    private LanguageMapping() {}

    private static final Map<String, String> CODE_TO_DESC = Map.ofEntries(
            Map.entry("CHS", "简体中文"),
            Map.entry("CHT", "繁体中文"),
            Map.entry("JPN", "日语"),
            Map.entry("ENG", "英语"),
            Map.entry("KOR", "韩语"),
            Map.entry("FRA", "法语"),
            Map.entry("DEU", "德语"),
            Map.entry("RUS", "俄语"),
            Map.entry("ESP", "西班牙语"),
            Map.entry("POR", "葡萄牙语"),
            Map.entry("ITA", "意大利语"),
            Map.entry("VIE", "越南语"),
            Map.entry("THA", "泰语"),
            Map.entry("ARA", "阿拉伯语")
    );

    /** 腾讯翻译 API 使用的语言代码 */
    private static final Map<String, String> CODE_TO_TENCENT = Map.of(
            "CHS", "zh", "CHT", "zh",
            "JPN", "ja", "ENG", "en",
            "KOR", "ko", "FRA", "fr",
            "DEU", "de", "RUS", "ru"
    );

    /** 将内部语言代码转为用于 Prompt 的可读描述 */
    public static String toDescription(String langCode) {
        if (langCode == null) return "简体中文";
        return CODE_TO_DESC.getOrDefault(langCode.toUpperCase(), langCode);
    }

    /** 将内部语言代码转为腾讯翻译 API 的语言代码 */
    public static String toTencentCode(String langCode) {
        if (langCode == null) return "auto";
        return CODE_TO_TENCENT.getOrDefault(langCode.toUpperCase(), "auto");
    }
}
