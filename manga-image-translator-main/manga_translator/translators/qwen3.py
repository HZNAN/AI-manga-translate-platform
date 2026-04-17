"""
Qwen3 翻译器 - 使用 Qwen3-4B-Instruct 模型进行本地翻译
模型来源: https://huggingface.co/Qwen/Qwen3-4B-Instruct-2507
"""

import os
import re
from typing import List

from ..config import TranslatorConfig
from .common import OfflineTranslator
from .config_gpt import ConfigGPT


class Qwen3Translator(OfflineTranslator, ConfigGPT):
    """Qwen3-4B-Instruct 翻译器"""
    _LANGUAGE_CODE_MAP = {
        'CHS': 'Simplified Chinese',
        'CHT': 'Traditional Chinese',
        'CSY': 'Czech',
        'NLD': 'Dutch',
        'ENG': 'English',
        'FRA': 'French',
        'DEU': 'German',
        'HUN': 'Hungarian',
        'ITA': 'Italian',
        'JPN': 'Japanese',
        'KOR': 'Korean',
        'POL': 'Polish',
        'PTB': 'Portuguese',
        'ROM': 'Romanian',
        'RUS': 'Russian',
        'ESP': 'Spanish',
        'TRK': 'Turkish',
        'UKR': 'Ukrainian',
        'VIN': 'Vietnamese',
        'CNR': 'Montenegrin',
        'SRP': 'Serbian',
        'HRV': 'Croatian',
        'ARA': 'Arabic',
        'THA': 'Thai',
        'IND': 'Indonesian'
    }

    # _TRANSLATOR_MODEL = "Qwen/Qwen3-4B-Instruct-2507"
    _TRANSLATOR_MODEL = "/root/autodl-tmp/manga-image-translator-main/translate_model/Qwen3-4B-Instruct-2507"
    _MODEL_SUB_DIR = os.path.join(OfflineTranslator._MODEL_DIR, OfflineTranslator._MODEL_SUB_DIR, _TRANSLATOR_MODEL)
    _IS_4_BIT = True
    _ENABLE_THINKING = False

    def __init__(self):
        OfflineTranslator.__init__(self)
        ConfigGPT.__init__(self, config_key='qwen2')  # 复用 qwen2 的配置模板

    def parse_args(self, args: TranslatorConfig):
        self.config = args.chatgpt_config

    async def _load(self, from_lang: str, to_lang: str, device: str):
        from transformers import (
            AutoModelForCausalLM,
            AutoTokenizer,
            BitsAndBytesConfig
        )
        self.device = device
        
        # 配置量化
        quantization_config = None
        if self._IS_4_BIT:
            quantization_config = BitsAndBytesConfig(load_in_4bit=True)
        
        self.logger.info(f'Loading Qwen3 model: {self._TRANSLATOR_MODEL}')
        
        self.model = AutoModelForCausalLM.from_pretrained(
            self._TRANSLATOR_MODEL,
            torch_dtype="auto",
            quantization_config=quantization_config,
            device_map="auto"
        )
        self.model.eval()
        self.tokenizer = AutoTokenizer.from_pretrained(self._TRANSLATOR_MODEL)
        
        self.logger.info('Qwen3 model loaded successfully')

    async def _unload(self):
        del self.model
        del self.tokenizer

    async def _infer(self, from_lang: str, to_lang: str, queries: List[str]) -> List[str]:
        model_inputs = self.tokenize(queries, to_lang)
        
        # 生成翻译
        generated_ids = self.model.generate(
            model_inputs.input_ids,
            attention_mask=model_inputs.attention_mask,
            max_new_tokens=10240
        )

        # 提取生成的 token
        generated_ids = [
            output_ids[len(input_ids):] for input_ids, output_ids in zip(model_inputs.input_ids, generated_ids)
        ]
        response = self.tokenizer.batch_decode(generated_ids, skip_special_tokens=True)[0]
        query_size = len(queries)

        translations = []
        self.logger.debug('-- Qwen3 Response --\n' + response)
        new_translations = re.split(r'<\|\d+\|>', response)

        # 当只有一个查询时，模型可能会省略 <|1|>
        if not new_translations[0].strip():
            new_translations = new_translations[1:]

        if len(new_translations) <= 1 and query_size > 1:
            # 尝试按换行符分割
            new_translations = re.split(r'\n', response)

        if len(new_translations) > query_size:
            new_translations = new_translations[: query_size]
        elif len(new_translations) < query_size:
            new_translations = new_translations + [''] * (query_size - len(new_translations))

        translations.extend([t.strip() for t in new_translations])
        return translations

    def tokenize(self, queries, to_lang):
        prompt = f"""Translate into {to_lang} and keep the original format.\n"""
        prompt += '\nOriginal:'
        for i, query in enumerate(queries):
            prompt += f'\n<|{i+1}|>{query}'

        tokenizer = self.tokenizer
        messages = [{'role': 'system', 'content': self.chat_system_template.format(to_lang=to_lang)}]
        
        if to_lang in self.chat_sample:
            messages.append({'role': 'user', 'content': self.chat_sample[to_lang][0]})
            messages.append({'role': 'assistant', 'content': self.chat_sample[to_lang][1]})
            
        messages.append({'role': 'user', 'content': prompt})

        self.logger.debug("-- Qwen3 prompt --\n" + 
                "\n".join(f"{msg['role'].capitalize()}:\n {msg['content']}" for msg in messages) +
                "\n"
            )

        text = tokenizer.apply_chat_template(
            messages,
            tokenize=False,
            add_generation_prompt=True,
            enable_thinking=self._ENABLE_THINKING 
        )

        # 确保 pad_token 设置正确
        if tokenizer.pad_token is None:
            tokenizer.pad_token = tokenizer.eos_token

        model_inputs = tokenizer(
            [text],
            return_tensors="pt",
            padding=True,
            truncation=True,
            max_length=self.tokenizer.model_max_length,
            return_attention_mask=True
        ).to(self.device)

        return model_inputs


class Qwen3BigTranslator(Qwen3Translator):
    """Qwen3-8B 翻译器 (4-bit 量化)"""
   #  _TRANSLATOR_MODEL = "Qwen/Qwen3-8B"
    _TRANSLATOR_MODEL = "/root/autodl-tmp/manga-image-translator-main/translate_model/Qwen3-8B"
    _MODEL_SUB_DIR = os.path.join(OfflineTranslator._MODEL_DIR, OfflineTranslator._MODEL_SUB_DIR, _TRANSLATOR_MODEL)
    _IS_4_BIT = True
    _ENABLE_THINKING = False
