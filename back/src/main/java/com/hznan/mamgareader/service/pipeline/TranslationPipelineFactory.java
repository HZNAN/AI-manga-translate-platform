package com.hznan.mamgareader.service.pipeline;

import com.hznan.mamgareader.model.enums.LlmProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 翻译管线工厂 — 根据 {@link LlmProvider} 类型选择具体的管线实现。
 * <p>
 * <b>工厂模式：</b>调用方只需传入 provider 字符串，工厂负责选择并返回正确的管线实例。
 * 新增翻译引擎时只需：
 * <ol>
 *   <li>在 {@link LlmProvider} 枚举中增加一项</li>
 *   <li>新建管线子类继承 {@link AbstractTranslationPipeline}，覆写 {@code doTranslate()}</li>
 *   <li>在本工厂的 switch 中增加一个分支</li>
 * </ol>
 * </p>
 */
@Component
@RequiredArgsConstructor // 生成参数构造函数，使用构造函数注入,且只有属性是final对象才能自动注入
public class TranslationPipelineFactory {

    private final LlmTranslationPipeline llmPipeline;
    private final TencentTranslationPipeline tencentPipeline;

    /**
     * 根据 provider 代码获取对应的翻译管线。
     *
     * @param providerCode 数据库中的 provider 值，如 "ollama"、"zhipu"、"tencent"
     * @return 匹配的翻译管线实例
     */
    public AbstractTranslationPipeline getPipeline(String providerCode) {
        LlmProvider provider = LlmProvider.fromCode(providerCode);

        return switch (provider.getModelType()) {
            case TENCENT_MT -> tencentPipeline;
            default -> llmPipeline;
        };
    }
}
