import io
import logging
import math
import os
import secrets
import shutil
import signal
import subprocess
import sys
import traceback
import cv2
from argparse import Namespace
import asyncio

sys.path.append(os.path.abspath(os.path.join(os.path.dirname(__file__), '..')))


from fastapi import FastAPI, Request, HTTPException, Header, UploadFile, File, Form
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import StreamingResponse, HTMLResponse, FileResponse
from fastapi.staticfiles import StaticFiles
from pathlib import Path

from manga_translator import Config
from server.instance import ExecutorInstance, executor_instances
from server.myqueue import task_queue
from server.request_extraction import get_ctx, while_streaming, TranslateRequest, BatchTranslateRequest, get_batch_ctx
from server.to_json import to_translation, TranslationResponse

app = FastAPI()
nonce = None

BASE_DIR = Path(__file__).resolve().parent
RESULT_ROOT = (BASE_DIR.parent / "result").resolve()
RESULT_ROOT.mkdir(parents=True, exist_ok=True)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# 添加result文件夹静态文件服务
if RESULT_ROOT.exists():
    app.mount("/result", StaticFiles(directory=str(RESULT_ROOT)), name="result")

@app.post("/register", response_description="no response", tags=["internal-api"])
async def register_instance(instance: ExecutorInstance, req: Request, req_nonce: str = Header(alias="X-Nonce")):
    if req_nonce != nonce:
        raise HTTPException(401, detail="Invalid nonce")
    instance.ip = req.client.host
    executor_instances.register(instance)

def transform_to_image(ctx):
    # 检查是否使用占位符（在web模式下final.png保存后会设置此标记）
    if hasattr(ctx, 'use_placeholder') and ctx.use_placeholder:
        # ctx.result已经是1x1占位符图片，快速传输
        img_byte_arr = io.BytesIO()
        ctx.result.save(img_byte_arr, format="PNG")
        return img_byte_arr.getvalue()

    # 返回完整的翻译结果
    img_byte_arr = io.BytesIO()
    ctx.result.save(img_byte_arr, format="PNG")
    return img_byte_arr.getvalue()

def transform_to_json(ctx):
    return to_translation(ctx).model_dump_json().encode("utf-8")

def transform_to_bytes(ctx):
    return to_translation(ctx).to_bytes()

@app.post("/translate/json", response_model=TranslationResponse, tags=["api", "json"],response_description="json strucure inspired by the ichigo translator extension")
async def json(req: Request, data: TranslateRequest):
    ctx = await get_ctx(req, data.config, data.image)
    return to_translation(ctx)

@app.post("/translate/bytes", response_class=StreamingResponse, tags=["api", "json"],response_description="custom byte structure for decoding look at examples in 'examples/response.*'")
async def bytes(req: Request, data: TranslateRequest):
    ctx = await get_ctx(req, data.config, data.image)
    return StreamingResponse(content=to_translation(ctx).to_bytes())

@app.post("/translate/image", response_description="the result image", tags=["api", "json"],response_class=StreamingResponse)
async def image(req: Request, data: TranslateRequest) -> StreamingResponse:
    ctx = await get_ctx(req, data.config, data.image)
    img_byte_arr = io.BytesIO()
    ctx.result.save(img_byte_arr, format="PNG")
    img_byte_arr.seek(0)

    return StreamingResponse(img_byte_arr, media_type="image/png")

@app.post("/translate/json/stream", response_class=StreamingResponse,tags=["api", "json"], response_description="A stream over elements with strucure(1byte status, 4 byte size, n byte data) status code are 0,1,2,3,4 0 is result data, 1 is progress report, 2 is error, 3 is waiting queue position, 4 is waiting for translator instance")
async def stream_json(req: Request, data: TranslateRequest) -> StreamingResponse:
    return await while_streaming(req, transform_to_json, data.config, data.image)

@app.post("/translate/bytes/stream", response_class=StreamingResponse, tags=["api", "json"],response_description="A stream over elements with strucure(1byte status, 4 byte size, n byte data) status code are 0,1,2,3,4 0 is result data, 1 is progress report, 2 is error, 3 is waiting queue position, 4 is waiting for translator instance")
async def stream_bytes(req: Request, data: TranslateRequest)-> StreamingResponse:
    return await while_streaming(req, transform_to_bytes,data.config, data.image)

@app.post("/translate/image/stream", response_class=StreamingResponse, tags=["api", "json"], response_description="A stream over elements with strucure(1byte status, 4 byte size, n byte data) status code are 0,1,2,3,4 0 is result data, 1 is progress report, 2 is error, 3 is waiting queue position, 4 is waiting for translator instance")
async def stream_image(req: Request, data: TranslateRequest) -> StreamingResponse:
    return await while_streaming(req, transform_to_image, data.config, data.image)

@app.post("/translate/with-form/json", response_model=TranslationResponse, tags=["api", "form"],response_description="json strucure inspired by the ichigo translator extension")
async def json_form(req: Request, image: UploadFile = File(...), config: str = Form("{}")):
    logger = logging.getLogger("manga_translator.server")
    try:
        img = await image.read()
        conf = Config.model_validate_json(config)
        ctx = await get_ctx(req, conf, img)
        return to_translation(ctx)
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"translate/with-form/json failed:\n{traceback.format_exc()}")
        raise HTTPException(status_code=500, detail=str(e))

@app.post("/translate/with-form/bytes", response_class=StreamingResponse, tags=["api", "form"],response_description="custom byte structure for decoding look at examples in 'examples/response.*'")
async def bytes_form(req: Request, image: UploadFile = File(...), config: str = Form("{}")):
    img = await image.read()
    conf = Config.model_validate_json(config)
    ctx = await get_ctx(req, conf, img)
    return StreamingResponse(content=to_translation(ctx).to_bytes())

@app.post("/translate/with-form/image", response_description="the result image", tags=["api", "form"],response_class=StreamingResponse)
async def image_form(req: Request, image: UploadFile = File(...), config: str = Form("{}")) -> StreamingResponse:
    logger = logging.getLogger("manga_translator.server")
    try:
        img = await image.read()
        conf = Config.model_validate_json(config)
        ctx = await get_ctx(req, conf, img)
        img_byte_arr = io.BytesIO()
        ctx.result.save(img_byte_arr, format="PNG")
        img_byte_arr.seek(0)
        return StreamingResponse(img_byte_arr, media_type="image/png")
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"translate/with-form/image failed:\n{traceback.format_exc()}")
        raise HTTPException(status_code=500, detail=str(e))

@app.post("/translate/with-form/json/stream", response_class=StreamingResponse, tags=["api", "form"],response_description="A stream over elements with strucure(1byte status, 4 byte size, n byte data) status code are 0,1,2,3,4 0 is result data, 1 is progress report, 2 is error, 3 is waiting queue position, 4 is waiting for translator instance")
async def stream_json_form(req: Request, image: UploadFile = File(...), config: str = Form("{}")) -> StreamingResponse:
    img = await image.read()
    conf = Config.model_validate_json(config)
    # 标记这是Web前端调用，用于占位符优化
    conf._is_web_frontend = True
    return await while_streaming(req, transform_to_json, conf, img)



@app.post("/translate/with-form/bytes/stream", response_class=StreamingResponse,tags=["api", "form"], response_description="A stream over elements with strucure(1byte status, 4 byte size, n byte data) status code are 0,1,2,3,4 0 is result data, 1 is progress report, 2 is error, 3 is waiting queue position, 4 is waiting for translator instance")
async def stream_bytes_form(req: Request, image: UploadFile = File(...), config: str = Form("{}"))-> StreamingResponse:
    img = await image.read()
    conf = Config.model_validate_json(config)
    return await while_streaming(req, transform_to_bytes, conf, img)

@app.post("/translate/with-form/image/stream", response_class=StreamingResponse, tags=["api", "form"], response_description="Standard streaming endpoint - returns complete image data. Suitable for API calls and scripts.")
async def stream_image_form(req: Request, image: UploadFile = File(...), config: str = Form("{}")) -> StreamingResponse:
    """通用流式端点：返回完整图片数据，适用于API调用和comicread脚本"""
    img = await image.read()
    conf = Config.model_validate_json(config)
    # 标记为通用模式，不使用占位符优化
    conf._web_frontend_optimized = False
    return await while_streaming(req, transform_to_image, conf, img)

@app.post("/translate/with-form/image/stream/web", response_class=StreamingResponse, tags=["api", "form"], response_description="Web frontend optimized streaming endpoint - uses placeholder optimization for faster response.")
async def stream_image_form_web(req: Request, image: UploadFile = File(...), config: str = Form("{}")) -> StreamingResponse:
    """Web前端专用端点：使用占位符优化，提供极速体验"""
    img = await image.read()
    conf = Config.model_validate_json(config)
    # 标记为Web前端优化模式，使用占位符优化
    conf._web_frontend_optimized = True
    return await while_streaming(req, transform_to_image, conf, img)

@app.post("/queue-size", response_model=int, tags=["api", "json"])
async def queue_size() -> int:
    return len(task_queue.queue)


@app.api_route("/result/{folder_name}/final.png", methods=["GET", "HEAD"], tags=["api", "file"])
async def get_result_by_folder(folder_name: str):
    """根据文件夹名称获取翻译结果图片"""
    result_dir = RESULT_ROOT
    if not result_dir.exists():
        raise HTTPException(404, detail="Result directory not found")

    folder_path = result_dir / folder_name
    if not folder_path.exists() or not folder_path.is_dir():
        raise HTTPException(404, detail=f"Folder {folder_name} not found")

    final_png_path = folder_path / "final.png"
    if not final_png_path.exists():
        raise HTTPException(404, detail="final.png not found in folder")

    async def file_iterator():
        with open(final_png_path, "rb") as f:
            yield f.read()

    return StreamingResponse(
        file_iterator(),
        media_type="image/png",
        headers={"Content-Disposition": f"inline; filename=final.png"}
    )

@app.post("/translate/batch/json", response_model=list[TranslationResponse], tags=["api", "json", "batch"])
async def batch_json(req: Request, data: BatchTranslateRequest):
    """Batch translate images and return JSON format results"""
    results = await get_batch_ctx(req, data.config, data.images, data.batch_size)
    return [to_translation(ctx) for ctx in results]

@app.post("/translate/batch/images", response_description="Zip file containing translated images", tags=["api", "batch"])
async def batch_images(req: Request, data: BatchTranslateRequest):
    """Batch translate images and return zip archive containing translated images"""
    import zipfile
    import tempfile
    
    results = await get_batch_ctx(req, data.config, data.images, data.batch_size)
    
    # Create temporary ZIP file
    with tempfile.NamedTemporaryFile(delete=False, suffix='.zip') as tmp_file:
        with zipfile.ZipFile(tmp_file, 'w') as zip_file:
            for i, ctx in enumerate(results):
                if ctx.result:
                    img_byte_arr = io.BytesIO()
                    ctx.result.save(img_byte_arr, format="PNG")
                    zip_file.writestr(f"translated_{i+1}.png", img_byte_arr.getvalue())
        
        # Return ZIP file
        with open(tmp_file.name, 'rb') as f:
            zip_data = f.read()
        
        # Clean up temporary file
        os.unlink(tmp_file.name)
        
        return StreamingResponse(
            io.BytesIO(zip_data),
            media_type="application/zip",
            headers={"Content-Disposition": "attachment; filename=translated_images.zip"}
        )

@app.get("/", response_class=HTMLResponse,tags=["ui"])
async def index() -> HTMLResponse:
    script_directory = Path(__file__).parent
    html_file = script_directory / "index.html"
    html_content = html_file.read_text(encoding="utf-8")
    return HTMLResponse(content=html_content)

@app.get("/manual", response_class=HTMLResponse, tags=["ui"])
async def manual():
    script_directory = Path(__file__).parent
    html_file = script_directory / "manual.html"
    html_content = html_file.read_text(encoding="utf-8")
    return HTMLResponse(content=html_content)

def generate_nonce():
    return secrets.token_hex(16)

def start_translator_client_proc(host: str, port: int, nonce: str, params: Namespace):
    cmds = [
        sys.executable,
        '-m', 'manga_translator',
        'shared',
        '--host', host,
        '--port', str(port),
        '--nonce', nonce,
    ]
    if params.use_gpu:
        cmds.append('--use-gpu')
    if params.use_gpu_limited:
        cmds.append('--use-gpu-limited')
    if params.ignore_errors:
        cmds.append('--ignore-errors')
    if params.verbose:
        cmds.append('--verbose')
    if params.models_ttl:
        cmds.append('--models-ttl=%s' % params.models_ttl)
    if getattr(params, 'pre_dict', None):
        cmds.extend(['--pre-dict', params.pre_dict])
    if getattr(params, 'post_dict', None):
        cmds.extend(['--post-dict', params.post_dict])       
    base_path = os.path.dirname(os.path.abspath(__file__))
    parent = os.path.dirname(base_path)
    proc = subprocess.Popen(cmds, cwd=parent)
    executor_instances.register(ExecutorInstance(ip=host, port=port))

    def handle_exit_signals(signal, frame):
        proc.terminate()
        sys.exit(0)

    signal.signal(signal.SIGINT, handle_exit_signals)
    signal.signal(signal.SIGTERM, handle_exit_signals)

    return proc

def prepare(args):
    global nonce
    if args.nonce is None:
        nonce = os.getenv('MT_WEB_NONCE', generate_nonce())
    else:
        nonce = args.nonce
    if args.start_instance:
        return start_translator_client_proc(args.host, args.port + 100, nonce, args)
    folder_name= "upload-cache"
    if os.path.exists(folder_name):
        shutil.rmtree(folder_name)
    os.makedirs(folder_name)

@app.post("/simple_execute/translate_batch", tags=["internal-api"])
async def simple_execute_batch(req: Request, data: BatchTranslateRequest):
    """Internal batch translation execution endpoint"""
    # Implementation for batch translation logic
    # Currently returns empty results, actual implementation needs to call batch translator
    from manga_translator import MangaTranslator
    translator = MangaTranslator({'batch_size': data.batch_size})
    
    # Prepare image-config pairs
    images_with_configs = [(img, data.config) for img in data.images]
    
    # Execute batch translation
    results = await translator.translate_batch(images_with_configs, data.batch_size)
    
    return results

@app.post("/execute/translate_batch", tags=["internal-api"])
async def execute_batch_stream(req: Request, data: BatchTranslateRequest):
    """Internal batch translation streaming execution endpoint"""
    # Streaming batch translation implementation
    from manga_translator import MangaTranslator
    translator = MangaTranslator({'batch_size': data.batch_size})
    
    # Prepare image-config pairs
    images_with_configs = [(img, data.config) for img in data.images]
    
    # Execute batch translation (streaming version requires more complex implementation)
    results = await translator.translate_batch(images_with_configs, data.batch_size)
    
    return results

@app.get("/results/list", tags=["api"])
async def list_results():
    """List all result directories"""
    result_dir = RESULT_ROOT
    if not result_dir.exists():
        return {"directories": []}
    
    try:
        directories = []
        for item_path in result_dir.iterdir():
            if item_path.is_dir():
                # Check if final.png exists in this directory
                final_png_path = item_path / "final.png"
                if final_png_path.exists():
                    directories.append(item_path.name)
        return {"directories": directories}
    except Exception as e:
        raise HTTPException(500, detail=f"Error listing results: {str(e)}")

@app.delete("/results/clear", tags=["api"])
async def clear_results():
    """Delete all result directories"""
    result_dir = RESULT_ROOT
    if not result_dir.exists():
        return {"message": "No results directory found"}
    
    try:
        deleted_count = 0
        for item_path in result_dir.iterdir():
            if item_path.is_dir():
                # Check if final.png exists in this directory
                final_png_path = item_path / "final.png"
                if final_png_path.exists():
                    shutil.rmtree(item_path)
                    deleted_count += 1
        
        return {"message": f"Deleted {deleted_count} result directories"}
    except Exception as e:
        raise HTTPException(500, detail=f"Error clearing results: {str(e)}")

@app.delete("/results/{folder_name}", tags=["api"])
async def delete_result(folder_name: str):
    """Delete a specific result directory"""
    result_dir = RESULT_ROOT
    folder_path = result_dir / folder_name
    
    if not folder_path.exists():
        raise HTTPException(404, detail="Result directory not found")
    
    try:
        # Check if final.png exists in this directory
        final_png_path = folder_path / "final.png"
        if not final_png_path.exists():
            raise HTTPException(404, detail="Result file not found")
        
        shutil.rmtree(folder_path)
        return {"message": f"Deleted result directory: {folder_name}"}
    except Exception as e:
        raise HTTPException(500, detail=f"Error deleting result: {str(e)}")

##############################################################################
# Step APIs – decoupled pipeline for Java backend orchestration
##############################################################################

@app.post("/step/detect-and-ocr", tags=["step-api"])
async def step_detect_and_ocr(image: UploadFile = File(...), config: str = Form("{}")):
    """
    Step 1: Detection + OCR.
    Directly invokes detection and OCR core functions without the executor subprocess.
    Returns JSON with bubble_coords, original_texts, raw_mask (base64), etc.
    """
    import base64
    import json
    import numpy as np
    from PIL import Image
    from manga_translator.detection import dispatch as dispatch_detection
    from manga_translator.ocr import dispatch as dispatch_ocr
    from manga_translator.textline_merge import dispatch as dispatch_textline_merge
    from manga_translator.textline_merge import dispatch_saber as dispatch_textline_merge_saber

    logger = logging.getLogger("manga_translator.step_api")
    try:
        img_bytes = await image.read()
        pil_img = Image.open(io.BytesIO(img_bytes)).convert("RGB")
        img_rgb = np.array(pil_img)

        conf = Config.model_validate_json(config)

        detector_key = conf.detector.detector
        detect_size = conf.detector.detection_size
        text_threshold = conf.detector.text_threshold
        box_threshold = conf.detector.box_threshold
        unclip_ratio = conf.detector.unclip_ratio

        textlines, mask_raw, mask = await dispatch_detection(
            detector_key, img_rgb, detect_size,
            text_threshold, box_threshold, unclip_ratio,
            False, False, False, False, 'cpu', False
        )

        ocr_key = conf.ocr.ocr
        im_h, im_w = img_rgb.shape[:2]

        if conf.ocr.use_mocr_merge and textlines:
            # Saber-Translator style: relaxed merge FIRST, then OCR each merged block
            text_regions = await dispatch_textline_merge_saber(
                textlines, im_w, im_h, verbose=False
            )
            source_lang = conf.ocr.source_lang or 'japanese'
            from manga_translator.ocr import get_ocr
            from manga_translator.ocr.model_paddle_vl import ModelPaddleVLOCR
            ocr_engine = get_ocr(ocr_key)

            for idx, region in enumerate(text_regions):
                x1, y1, x2, y2 = [int(v) for v in region.xyxy]
                x1, y1 = max(0, x1), max(0, y1)
                x2, y2 = min(im_w, x2), min(im_h, y2)
                if x2 <= x1 or y2 <= y1:
                    continue
                crop = img_rgb[y1:y2, x1:x2]
                if crop.size == 0:
                    continue
                if isinstance(ocr_engine, ModelPaddleVLOCR):
                    text = ocr_engine.recognize_crop(crop, source_lang)
                else:
                    from manga_translator.utils import Quadrilateral
                    h, w = crop.shape[:2]
                    pts = np.array([[0, 0], [w, 0], [w, h], [0, h]], dtype=np.float64)
                    tmp_quad = Quadrilateral(pts, '', 1.0)
                    result = await ocr_engine.recognize(crop, [tmp_quad], conf.ocr, verbose=False)
                    text = result[0].text if result else ''
                region.text = text
                logger.info(f'OCR block [{idx+1}/{len(text_regions)}]: "{text[:40]}"')
        else:
            if textlines:
                textlines = await dispatch_ocr(ocr_key, img_rgb, textlines, conf.ocr, 'cpu', False)
            text_regions = await dispatch_textline_merge(
                textlines, im_w, im_h, verbose=False
            )

        bubble_coords = []
        original_texts = []
        auto_directions = []
        font_sizes = []
        fg_colors = []
        bg_colors = []
        text_regions_data = []

        for region in text_regions:
            x1, y1, x2, y2 = [int(v) for v in region.xyxy]
            bubble_coords.append([x1, y1, x2, y2])
            original_texts.append(region.text if region.text else "")
            auto_directions.append("v" if region.vertical else "h")
            font_sizes.append(int(region.font_size) if region.font_size > 0 else -1)

            try:
                fc = np.array(region.fg_colors).astype(int).tolist()
                bc = np.array(region.bg_colors).astype(int).tolist()
                fg_colors.append(fc if len(fc) == 3 else [0, 0, 0])
                bg_colors.append(bc if len(bc) == 3 else [255, 255, 255])
            except Exception:
                fg_colors.append([0, 0, 0])
                bg_colors.append([255, 255, 255])

            region_data = {
                "lines": region.lines.tolist(),
                "texts": region.texts if region.texts else [],
                "font_size": int(region.font_size) if region.font_size > 0 else -1,
                "angle": float(region.angle),
                "direction": region._direction if hasattr(region, '_direction') else 'auto',
                "alignment": region._alignment if hasattr(region, '_alignment') else 'auto',
                "fg_color": fc if len(fc) == 3 else [0, 0, 0],
                "bg_color": bc if len(bc) == 3 else [255, 255, 255],
                "language": region.language if hasattr(region, 'language') else 'unknown',
                "text": region.text if region.text else "",
            }
            text_regions_data.append(region_data)

        raw_mask_b64 = None
        if mask_raw is not None:
            _, buf = cv2.imencode('.png', mask_raw)
            raw_mask_b64 = base64.b64encode(buf).decode('utf-8')

        mask_b64 = None
        if mask is not None:
            _, buf = cv2.imencode('.png', mask)
            mask_b64 = base64.b64encode(buf).decode('utf-8')

        return {
            "bubble_coords": bubble_coords,
            "original_texts": original_texts,
            "auto_directions": auto_directions,
            "font_sizes": font_sizes,
            "fg_colors": fg_colors,
            "bg_colors": bg_colors,
            "raw_mask": raw_mask_b64,
            "mask": mask_b64,
            "region_count": len(bubble_coords),
            "text_regions_data": text_regions_data,
        }

    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"step/detect-and-ocr failed:\n{traceback.format_exc()}")
        raise HTTPException(status_code=500, detail=str(e))


@app.post("/step/inpaint", tags=["step-api"])
async def step_inpaint(image: UploadFile = File(...),
                       bubble_coords: str = Form("[]"),
                       raw_mask: str = Form(""),
                       config: str = Form("{}")):
    """
    Step 2: Inpainting.
    Takes original image + bubble_coords + raw_mask, returns clean (text-removed) image.
    """
    import base64
    import json
    import numpy as np
    from PIL import Image
    from manga_translator.inpainting import dispatch as dispatch_inpainting
    from manga_translator.mask_refinement import dispatch_sync as dispatch_mask_refinement_sync
    from manga_translator.utils import TextBlock

    logger = logging.getLogger("manga_translator.step_api")
    try:
        img_bytes = await image.read()
        pil_img = Image.open(io.BytesIO(img_bytes)).convert("RGB")
        img_rgb = np.array(pil_img)
        h, w = img_rgb.shape[:2]

        conf = Config.model_validate_json(config)
        coords_list = json.loads(bubble_coords)

        mask_np = None
        if raw_mask:
            mask_data = base64.b64decode(raw_mask)
            mask_pil = Image.open(io.BytesIO(mask_data))
            mask_np = np.array(mask_pil)
            if mask_np.ndim == 3:
                mask_np = mask_np[:, :, 0]
            if mask_np.shape[:2] != (h, w):
                mask_np = cv2.resize(mask_np, (w, h), interpolation=cv2.INTER_LINEAR)

        if mask_np is None:
            mask_np = np.zeros((h, w), dtype=np.uint8)
            for coord in coords_list:
                x1, y1, x2, y2 = [int(c) for c in coord]
                mask_np[y1:y2, x1:x2] = 255

        text_regions = []
        for coord in coords_list:
            x1, y1, x2, y2 = [int(c) for c in coord]
            pts = np.array([[x1, y1], [x2, y1], [x2, y2], [x1, y2]], dtype=np.float64)
            tb = TextBlock([pts], texts=[""], translation="", font_size=-1)
            text_regions.append(tb)

        try:
            refined_mask = await asyncio.to_thread(
                dispatch_mask_refinement_sync,
                text_regions, img_rgb, mask_np,
                'fit_text', conf.mask_dilation_offset, 0, False, conf.kernel_size
            )
        except Exception:
            logger.warning("Mask refinement failed, using raw mask")
            refined_mask = mask_np

        # Gaussian blur on mask edges for smoother inpaint blending (Saber-Translator style)
        hard_mask = (refined_mask > 127).astype(np.uint8) * 255
        blur_size = 7
        blurred = cv2.GaussianBlur(refined_mask, (blur_size, blur_size), 0)
        refined_mask = np.where(hard_mask == 255, hard_mask, blurred)

        inpainted = await dispatch_inpainting(
            conf.inpainter.inpainter,
            img_rgb, refined_mask,
            conf.inpainter,
            conf.inpainter.inpainting_size,
            'cpu', False
        )

        result_pil = Image.fromarray(inpainted)
        img_byte_arr = io.BytesIO()
        result_pil.save(img_byte_arr, format="PNG")
        img_byte_arr.seek(0)
        return StreamingResponse(img_byte_arr, media_type="image/png")

    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"step/inpaint failed:\n{traceback.format_exc()}")
        raise HTTPException(status_code=500, detail=str(e))


@app.post("/step/render", tags=["step-api"])
async def step_render(image: UploadFile = File(...),
                      translations: str = Form("[]"),
                      text_regions_data: str = Form("[]"),
                      config: str = Form("{}")):
    """
    Step 3: Rendering.
    Takes clean (inpainted) image + text_regions_data (serialized native TextBlocks from
    detect-and-ocr step) + translations (translated strings per region).
    Reconstructs original TextBlocks, injects translations, and calls native
    rendering.dispatch for high-quality layout.

    translations: JSON array of translated strings, one per text_region, same order.
                  e.g. ["译文1", "译文2", ...]
    text_regions_data: JSON array of serialized TextBlock data from /step/detect-and-ocr.
    """
    import json
    import numpy as np
    from PIL import Image
    from manga_translator.utils import TextBlock
    from manga_translator.rendering import dispatch_sync as dispatch_rendering_sync

    logger = logging.getLogger("manga_translator.step_api")
    try:
        img_bytes = await image.read()
        pil_img = Image.open(io.BytesIO(img_bytes)).convert("RGB")
        img_array = np.array(pil_img)

        conf = Config.model_validate_json(config)
        translated_texts = json.loads(translations)
        regions_raw = json.loads(text_regions_data)
        target_lang = conf.translator.target_lang or "CHS"

        if not regions_raw or not translated_texts:
            result_pil = Image.fromarray(img_array)
            buf = io.BytesIO()
            result_pil.save(buf, format="PNG")
            buf.seek(0)
            return StreamingResponse(buf, media_type="image/png")

        text_blocks = []
        for i, rd in enumerate(regions_raw):
            trans = translated_texts[i] if i < len(translated_texts) else ""
            if not trans or not trans.strip():
                continue

            lines = rd.get("lines", [])
            if not lines:
                continue

            tb = TextBlock(
                lines=lines,
                texts=rd.get("texts", [""]),
                language=rd.get("language", "unknown"),
                font_size=rd.get("font_size", -1),
                angle=rd.get("angle", 0),
                translation=trans,
                fg_color=tuple(rd.get("fg_color", [0, 0, 0])),
                bg_color=tuple(rd.get("bg_color", [255, 255, 255])),
                direction=rd.get("direction", "auto"),
                alignment=rd.get("alignment", "auto"),
                target_lang=target_lang,
                source_lang="",
            )
            tb.text_raw = rd.get("text", "")
            text_blocks.append(tb)

        if text_blocks:
            rendered = await asyncio.to_thread(
                dispatch_rendering_sync,
                img_array, text_blocks,
                '',
                conf.render.font_size if conf.render.font_size else None,
                conf.render.font_size_offset or 0,
                conf.render.font_size_minimum if hasattr(conf.render, 'font_size_minimum') else 0,
                not conf.render.no_hyphenation,
                None,
                conf.render.line_spacing if hasattr(conf.render, 'line_spacing') else None,
            )
        else:
            rendered = img_array

        result_pil = Image.fromarray(rendered)
        buf = io.BytesIO()
        result_pil.save(buf, format="PNG")
        buf.seek(0)
        return StreamingResponse(buf, media_type="image/png")

    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"step/render failed:\n{traceback.format_exc()}")
        raise HTTPException(status_code=500, detail=str(e))


@app.post("/render-translated", tags=["api"], deprecated=True)
async def render_translated(
    image: UploadFile = File(...),
    translations: str = Form(...),
    config: str = Form("{}")
):
    """
    Render translated text onto an inpainted image.
    translations: JSON array of regions with translatedText, minX, minY, maxX, maxY, etc.
    """
    logger = logging.getLogger("manga_translator.server")
    try:
        import json
        import numpy as np
        from PIL import Image
        from manga_translator.utils.textblock import TextBlock
        from manga_translator.rendering import dispatch as render_dispatch

        image_bytes = await image.read()
        pil_img = Image.open(io.BytesIO(image_bytes)).convert("RGB")
        img_array = np.array(pil_img)

        regions_data = json.loads(translations)
        if not regions_data:
            img_byte_arr = io.BytesIO()
            pil_img.save(img_byte_arr, format="PNG")
            return StreamingResponse(io.BytesIO(img_byte_arr.getvalue()), media_type="image/png")

        text_blocks = []
        for region in regions_data:
            min_x = int(region.get("minX", 0))
            min_y = int(region.get("minY", 0))
            max_x = int(region.get("maxX", 0))
            max_y = int(region.get("maxY", 0))
            translated_text = region.get("translatedText", "")
            if not translated_text or (min_x == max_x and min_y == max_y):
                continue

            pts = [[min_x, min_y], [max_x, min_y], [max_x, max_y], [min_x, max_y]]

            fg_color = tuple(region.get("fgColor", [0, 0, 0]))
            bg_color = tuple(region.get("bgColor", [255, 255, 255]))
            direction = region.get("direction", "auto")
            target_lang = region.get("targetLang", "CHS")

            region_w = max(max_x - min_x, 1)
            region_h = max(max_y - min_y, 1)

            cjk_count = sum(1 for c in translated_text if ord(c) > 0x2E80)
            latin_count = len(translated_text) - cjk_count
            effective_len = max(cjk_count + latin_count * 0.5, 1)

            estimated_fs = int(math.sqrt(region_w * region_h / (effective_len * 1.5)))
            estimated_fs = max(estimated_fs, 12)
            estimated_fs = min(estimated_fs, int(region_h * 0.5), 80)

            chars_per_line = max(region_w / estimated_fs, 1)
            num_lines = max(1, math.ceil(effective_len / chars_per_line))
            placeholder_texts = [""] * num_lines

            tb = TextBlock(
                lines=[pts],
                texts=placeholder_texts,
                translation=translated_text,
                font_size=estimated_fs,
                fg_color=fg_color,
                bg_color=bg_color,
                direction=direction,
                target_lang=target_lang,
                source_lang="",
            )
            text_blocks.append(tb)

        if text_blocks:
            rendered = await render_dispatch(img_array, text_blocks)
        else:
            rendered = img_array

        result_img = Image.fromarray(rendered)
        img_byte_arr = io.BytesIO()
        result_img.save(img_byte_arr, format="PNG")
        img_byte_arr.seek(0)
        return StreamingResponse(img_byte_arr, media_type="image/png")
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"render-translated failed:\n{traceback.format_exc()}")
        raise HTTPException(status_code=500, detail=str(e))


#todo: restart if crash
#todo: cache results
#todo: cleanup cache

if __name__ == '__main__':
    import uvicorn
    from args import parse_arguments

    args = parse_arguments()
    args.start_instance = True
    proc = prepare(args)
    print("Nonce: "+nonce)
    try:
        uvicorn.run(app, host=args.host, port=args.port)
    except Exception:
        if proc:
            proc.terminate()
