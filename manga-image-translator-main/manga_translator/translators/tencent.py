"""
腾讯翻译器 - 使用腾讯云机器翻译 API
文档: https://cloud.tencent.com/document/product/551/15619
"""

import hashlib
import hmac
import json
import time
from datetime import datetime
import aiohttp

from .common import CommonTranslator, InvalidServerResponse, MissingAPIKeyException
from .keys import TENCENT_SECRET_ID, TENCENT_SECRET_KEY

# 腾讯云 API 配置
SERVICE = "tmt"
HOST = "tmt.tencentcloudapi.com"
ENDPOINT = f"https://{HOST}"
REGION = "ap-shanghai"  # 广州区域
VERSION = "2018-03-21"
ACTION = "TextTranslate"


class TencentTranslator(CommonTranslator):
    _LANGUAGE_CODE_MAP = {
        'CHS': 'zh',
        'CHT': 'zh-TW',
        'JPN': 'ja',
        'ENG': 'en',
        'KOR': 'ko',
        'VIN': 'vi',
        'CSY': 'cs',
        'NLD': 'nl',
        'FRA': 'fr',
        'DEU': 'de',
        'HUN': 'hu',
        'ITA': 'it',
        'POL': 'pl',
        'PTB': 'pt',
        'ROM': 'ro',
        'RUS': 'ru',
        'ESP': 'es',
        'THA': 'th',
        'TRK': 'tr',
        'ARA': 'ar',
        'IND': 'id',
    }
    _INVALID_REPEAT_COUNT = 1

    def __init__(self) -> None:
        super().__init__()
        if not TENCENT_SECRET_ID or not TENCENT_SECRET_KEY:
            raise MissingAPIKeyException(
                'Please set the TENCENT_SECRET_ID and TENCENT_SECRET_KEY environment variables before using the tencent translator.'
            )

    def _sign(self, key: bytes, msg: str) -> bytes:
        return hmac.new(key, msg.encode("utf-8"), hashlib.sha256).digest()

    def _get_authorization(self, timestamp: int, payload: str) -> str:
        """生成腾讯云 API v3 签名"""
        date = datetime.utcfromtimestamp(timestamp).strftime("%Y-%m-%d")
        
        # 步骤1: 拼接规范请求串
        http_request_method = "POST"
        canonical_uri = "/"
        canonical_querystring = ""
        canonical_headers = f"content-type:application/json; charset=utf-8\nhost:{HOST}\nx-tc-action:{ACTION.lower()}\n"
        signed_headers = "content-type;host;x-tc-action"
        hashed_request_payload = hashlib.sha256(payload.encode("utf-8")).hexdigest()
        canonical_request = f"{http_request_method}\n{canonical_uri}\n{canonical_querystring}\n{canonical_headers}\n{signed_headers}\n{hashed_request_payload}"
        
        # 步骤2: 拼接待签名字符串
        algorithm = "TC3-HMAC-SHA256"
        credential_scope = f"{date}/{SERVICE}/tc3_request"
        hashed_canonical_request = hashlib.sha256(canonical_request.encode("utf-8")).hexdigest()
        string_to_sign = f"{algorithm}\n{timestamp}\n{credential_scope}\n{hashed_canonical_request}"
        
        # 步骤3: 计算签名
        secret_date = self._sign(("TC3" + TENCENT_SECRET_KEY).encode("utf-8"), date)
        secret_service = self._sign(secret_date, SERVICE)
        secret_signing = self._sign(secret_service, "tc3_request")
        signature = hmac.new(secret_signing, string_to_sign.encode("utf-8"), hashlib.sha256).hexdigest()
        
        # 步骤4: 拼接 Authorization
        authorization = f"{algorithm} Credential={TENCENT_SECRET_ID}/{credential_scope}, SignedHeaders={signed_headers}, Signature={signature}"
        return authorization

    async def _translate(self, from_lang: str, to_lang: str, queries: list) -> list:
        translations = []
        
        for query in queries:
            # 构建请求体
            payload = json.dumps({
                "SourceText": query,
                "Source": from_lang if from_lang != 'auto' else 'auto',
                "Target": to_lang,
                "ProjectId": 0
            })
            
            timestamp = int(time.time())
            authorization = self._get_authorization(timestamp, payload)
            
            headers = {
                "Authorization": authorization,
                "Content-Type": "application/json; charset=utf-8",
                "Host": HOST,
                "X-TC-Action": ACTION,
                "X-TC-Timestamp": str(timestamp),
                "X-TC-Version": VERSION,
                "X-TC-Region": REGION,
            }
            
            async with aiohttp.ClientSession() as session:
                async with session.post(ENDPOINT, headers=headers, data=payload) as resp:
                    result = await resp.json()
            
            if "Response" not in result:
                raise InvalidServerResponse(f'Tencent returned invalid response: {result}')
            
            response = result["Response"]
            if "Error" in response:
                raise InvalidServerResponse(
                    f'Tencent translation error: {response["Error"]["Code"]} - {response["Error"]["Message"]}'
                )
            
            if "TargetText" not in response:
                raise InvalidServerResponse(f'Tencent returned invalid response: {result}')
            
            translations.append(response["TargetText"])
        
        return translations