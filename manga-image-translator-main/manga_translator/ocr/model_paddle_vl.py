import os
import logging
import numpy as np
from typing import List, Optional

from .common import CommonOCR
from ..config import OcrConfig
from ..utils import Quadrilateral
from ..utils.generic import BASE_PATH

logger = logging.getLogger('ModelPaddleVLOCR')

PADDLEOCR_VL_MODEL_DIR = os.path.join(BASE_PATH, 'models', 'paddleocr_vl')
PADDLEOCR_VL_HF_MODEL = 'jzhang533/PaddleOCR-VL-For-Manga'

LANG_TO_PROMPT = {
    'japanese': '日语',
    'japan': '日语',
    'JPN': '日语',
    'chinese': '简体中文',
    'CHS': '简体中文',
    'CHT': '繁体中文',
    'chinese_cht': '繁体中文',
    'en': '英语',
    'ENG': '英语',
    'english': '英语',
    'korean': '韩语',
    'KOR': '韩语',
    'french': '法语',
    'FRA': '法语',
    'german': '德语',
    'DEU': '德语',
    'spanish': '西班牙语',
    'ESP': '西班牙语',
    'italian': '意大利语',
    'ITA': '意大利语',
    'portuguese': '葡萄牙语',
    'PTB': '葡萄牙语',
    'russian': '俄语',
    'RUS': '俄语',
    'thai': '泰语',
    'vietnamese': '越南语',
    'indonesian': '印尼语',
    'arabic': '阿拉伯语',
    'turkish': '土耳其语',
    'greek': '希腊语',
    'hindi': '印地语',
}


def _check_transformers_version():
    """
    PaddleOCR-VL custom code requires transformers 4.46.x ~ 4.47.x.

    - ProcessingKwargs, TextKwargs, ImagesKwargs etc. added in 4.44
    - SlidingWindowCache removed in 4.48
    - Various breaking changes in 5.0

    Ref: https://huggingface.co/PaddlePaddle/PaddleOCR-VL/discussions/88
    """
    import transformers
    ver = tuple(int(x) for x in transformers.__version__.split('.')[:2])
    if ver < (4, 46) or ver >= (4, 48):
        logger.warning(
            f'transformers {transformers.__version__} may be incompatible with PaddleOCR-VL. '
            f'Recommended: pip install "transformers>=4.46.0,<4.48.0"'
        )

    import transformers.cache_utils as cache_mod
    if not hasattr(cache_mod, 'SlidingWindowCache'):
        cache_mod.SlidingWindowCache = cache_mod.DynamicCache
        logger.debug('Patched SlidingWindowCache -> DynamicCache')


class ModelPaddleVLOCR(CommonOCR):
    """
    PaddleOCR-VL OCR engine using transformers VLM.

    Uses the jzhang533/PaddleOCR-VL-For-Manga model (fine-tuned for manga)
    or can load from a local directory at models/paddleocr_vl/.

    For each detected text region (Quadrilateral), crops the region and
    performs VLM-based OCR via a chat-style prompt.
    """

    def __init__(self):
        super().__init__()
        self._model = None
        self._processor = None
        self._device = None
        self._torch_dtype = None

    def _get_model_path(self) -> str:
        if os.path.exists(PADDLEOCR_VL_MODEL_DIR) and \
           os.path.exists(os.path.join(PADDLEOCR_VL_MODEL_DIR, 'config.json')):
            return PADDLEOCR_VL_MODEL_DIR
        return PADDLEOCR_VL_HF_MODEL

    def _ensure_initialized(self):
        if self._model is not None:
            return

        _check_transformers_version()

        import torch
        from transformers import AutoProcessor, AutoModel

        model_path = self._get_model_path()
        is_local = model_path != PADDLEOCR_VL_HF_MODEL
        logger.info(f'Loading PaddleOCR-VL from {"local: " + model_path if is_local else "HuggingFace: " + model_path}')

        if torch.cuda.is_available():
            self._device = 'cuda'
            self._torch_dtype = torch.bfloat16
            logger.info(f'Using GPU: {torch.cuda.get_device_name(0)}')
        elif hasattr(torch.backends, 'mps') and torch.backends.mps.is_available():
            self._device = 'mps'
            self._torch_dtype = torch.float16
        else:
            self._device = 'cpu'
            self._torch_dtype = torch.float32
            logger.info('GPU not available, using CPU (inference will be slow)')

        self._processor = AutoProcessor.from_pretrained(
            model_path,
            trust_remote_code=True,
            use_fast=False,
        )

        self._model = AutoModel.from_pretrained(
            model_path,
            trust_remote_code=True,
            torch_dtype=self._torch_dtype,
            device_map=self._device if self._device != 'cpu' else None,
        )

        if self._device == 'cpu':
            self._model = self._model.to(self._device)

        self._model.eval()
        logger.info('PaddleOCR-VL model loaded successfully')

    def _recognize_single(self, img: np.ndarray, source_lang: str = 'japanese') -> str:
        import torch
        from PIL import Image as PILImage

        pil_img = PILImage.fromarray(img)
        if pil_img.mode != 'RGB':
            pil_img = pil_img.convert('RGB')

        lang_name = LANG_TO_PROMPT.get(source_lang, '日语')
        ocr_prompt = f'对图中的{lang_name}进行OCR:'

        messages = [
            {
                'role': 'user',
                'content': [
                    {'type': 'image', 'image': pil_img},
                    {'type': 'text', 'text': ocr_prompt},
                ],
            }
        ]

        text = self._processor.apply_chat_template(
            messages, tokenize=False, add_generation_prompt=True
        )

        inputs = self._processor(
            text=[text],
            images=[pil_img],
            return_tensors='pt',
            padding=True,
        )

        inputs = {k: v.to(self._device) if isinstance(v, torch.Tensor) else v
                  for k, v in inputs.items()}

        with torch.no_grad():
            generated_ids = self._model.generate(
                **inputs,
                max_new_tokens=256,
                do_sample=False,
            )

        input_len = inputs['input_ids'].shape[1]
        generated_ids_trimmed = generated_ids[:, input_len:]

        output_text = self._processor.batch_decode(
            generated_ids_trimmed,
            skip_special_tokens=True,
            clean_up_tokenization_spaces=False,
        )[0]

        return output_text.strip()

    def recognize_crop(self, crop: np.ndarray, source_lang: str = 'japanese') -> str:
        """
        Public method: initialize model if needed, OCR a single image crop.
        Called by the pipeline when doing merge-before-OCR (Saber-Translator style).
        """
        self._ensure_initialized()
        if crop.size == 0:
            return ''
        try:
            result = self._recognize_single(crop, source_lang)
            return result
        except Exception as e:
            logger.warning(f'PaddleOCR-VL recognize_crop failed: {e}')
            return ''

    async def _recognize(self, image: np.ndarray, textlines: List[Quadrilateral],
                         config: OcrConfig, verbose: bool = False) -> List[Quadrilateral]:
        self._ensure_initialized()

        source_lang = getattr(config, 'source_lang', 'japanese')
        im_h, im_w = image.shape[:2]

        for i, quad in enumerate(textlines):
            x1, y1, x2, y2 = [int(v) for v in quad.xyxy]
            x1, y1 = max(0, x1), max(0, y1)
            x2, y2 = min(im_w, x2), min(im_h, y2)

            if x2 <= x1 or y2 <= y1:
                quad.text = ''
                continue

            bubble_img = image[y1:y2, x1:x2]
            if bubble_img.size == 0:
                quad.text = ''
                continue

            try:
                recognized = self._recognize_single(bubble_img, source_lang)
                quad.text = recognized
                if verbose and recognized:
                    logger.info(f'PaddleOCR-VL [{i+1}/{len(textlines)}]: [{x1},{y1},{x2},{y2}] -> "{recognized}"')
            except Exception as e:
                logger.warning(f'PaddleOCR-VL failed on region ({x1},{y1},{x2},{y2}): {e}')
                quad.text = ''

        if self._device == 'cuda':
            import torch
            torch.cuda.empty_cache()

        return textlines
