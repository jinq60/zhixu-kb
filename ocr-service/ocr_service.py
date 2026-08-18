import logging
import os
from typing import Any, List

import numpy as np
from PIL import Image

from config import settings
from image_processor import preprocess, resize_if_needed

logger = logging.getLogger(__name__)

# PaddleX hoster connectivity probing can be slow/unreliable in restricted networks.
os.environ.setdefault("PADDLE_PDX_DISABLE_MODEL_SOURCE_CHECK", "True")

try:
    from rapidocr_onnxruntime import RapidOCR  # type: ignore

    RAPIDOCR_AVAILABLE = True
except Exception:  # pragma: no cover - optional dependency
    RapidOCR = None  # type: ignore
    RAPIDOCR_AVAILABLE = False
    logger.warning("RapidOCR not installed; fallback to PaddleOCR if available.")

try:
    from paddleocr import PaddleOCR  # type: ignore
    from paddleocr import __version__ as PADDLEOCR_VERSION  # type: ignore
except Exception:  # pragma: no cover - optional dependency
    PaddleOCR = None  # type: ignore
    PADDLEOCR_VERSION = "0.0.0"
    logger.warning("PaddleOCR not installed; OCR service will return 503 for recognize requests.")


class OCRService:
    def __init__(self, lang: str = "ch", use_angle: bool = True, use_gpu: bool = False):
        self.available = False
        self.ocr = None
        self.engine_kind = "none"
        self.unavailable_reason = ""
        self.is_v3 = str(PADDLEOCR_VERSION).split(".")[0].isdigit() and int(str(PADDLEOCR_VERSION).split(".")[0]) >= 3

        # Preferred engine: RapidOCR (onnxruntime, stable in containers / WSL2).
        if RAPIDOCR_AVAILABLE:
            try:
                self.ocr = RapidOCR()
                self.available = True
                self.engine_kind = "rapid"
                logger.info("RapidOCR initialized (onnxruntime)")
                return
            except Exception as exc:
                self.unavailable_reason = f"RapidOCR init failed: {type(exc).__name__}: {exc}"
                logger.warning(self.unavailable_reason)

        if PaddleOCR is None:
            self.unavailable_reason = self.unavailable_reason or "PaddleOCR not installed"
            return

        # Try legacy and new constructor styles to support PaddleOCR 2.x / 3.x.
        legacy_kwargs = {"lang": lang, "use_angle_cls": use_angle, "use_gpu": use_gpu}
        v3_base_kwargs = {
            "lang": lang,
            "use_doc_orientation_classify": False,
            "use_doc_unwarping": False,
            "use_textline_orientation": use_angle,
            "device": "gpu:0" if use_gpu else "cpu",
        }
        v3_safe_cpu_kwargs = {
            "lang": lang,
            "use_doc_orientation_classify": False,
            "use_doc_unwarping": False,
            "use_textline_orientation": False,
            "device": "cpu",
        }

        # Optional local model directories for offline init.
        if settings.PADDLE_DET_MODEL_DIR:
            legacy_kwargs["det_model_dir"] = settings.PADDLE_DET_MODEL_DIR
            v3_base_kwargs["text_detection_model_dir"] = settings.PADDLE_DET_MODEL_DIR
            v3_safe_cpu_kwargs["text_detection_model_dir"] = settings.PADDLE_DET_MODEL_DIR
        if settings.PADDLE_REC_MODEL_DIR:
            legacy_kwargs["rec_model_dir"] = settings.PADDLE_REC_MODEL_DIR
            v3_base_kwargs["text_recognition_model_dir"] = settings.PADDLE_REC_MODEL_DIR
            v3_safe_cpu_kwargs["text_recognition_model_dir"] = settings.PADDLE_REC_MODEL_DIR
        if settings.PADDLE_CLS_MODEL_DIR:
            legacy_kwargs["cls_model_dir"] = settings.PADDLE_CLS_MODEL_DIR
            v3_base_kwargs["textline_orientation_model_dir"] = settings.PADDLE_CLS_MODEL_DIR

        candidates = [
            legacy_kwargs,
            v3_base_kwargs,
            v3_safe_cpu_kwargs,
        ]
        errors: List[str] = []
        seen = set()
        for kwargs in candidates:
            key = tuple(sorted(kwargs.items()))
            if key in seen:
                continue
            seen.add(key)
            try:
                self.ocr = PaddleOCR(**kwargs)
                self.available = True
                logger.info("PaddleOCR initialized with args: %s", kwargs)
                return
            except Exception as exc:
                errors.append(f"{type(exc).__name__}: {exc}")
                logger.warning("PaddleOCR init failed with args %s: %s", kwargs, exc)

        self.unavailable_reason = "PaddleOCR init failed; " + " | ".join(errors[-2:])
        logger.error(self.unavailable_reason)

    def is_ready(self) -> bool:
        return self.available

    def recognize(self, image: Image.Image) -> List[str]:
        if not self.available or self.ocr is None:
            raise RuntimeError(f"OCR engine not available: {self.unavailable_reason}")

        base_image = resize_if_needed(image)
        # Try original image first for best fidelity; fallback to preprocessed image only when needed.
        candidates = [base_image]
        try:
            candidates.append(preprocess(base_image))
        except Exception:
            pass

        infer_errors: List[str] = []
        for candidate in candidates:
            try:
                lines = self._recognize_single(np.array(candidate))
                if lines:
                    return lines
            except RuntimeError as exc:
                infer_errors.append(str(exc))

        logger.warning("PaddleOCR produced no text lines after parsing")
        if infer_errors:
            logger.warning("PaddleOCR inference errors: %s", " | ".join(infer_errors[-2:]))
        return []

    def _recognize_single(self, img_array: np.ndarray) -> List[str]:
        result: Any = None
        errors: List[str] = []

        # RapidOCR style: engine(img) -> (result, elapse) with result [[box, text, score], ...]
        if self.engine_kind == "rapid":
            try:
                result, _elapse = self.ocr(img_array)
            except Exception as exc:
                errors.append(f"rapid(img): {exc}")
            lines = self._extract_lines(result)
            if lines:
                return lines
            if errors:
                raise RuntimeError("RapidOCR inference failed: " + " | ".join(errors[-3:]))
            return lines

        # PaddleOCR 3.x pipeline style
        if self.is_v3 and hasattr(self.ocr, "predict"):
            try:
                result = self.ocr.predict(img_array)
            except Exception as exc:
                errors.append(f"predict(img): {exc}")

        # PaddleOCR 2.x style
        if result is None and not self.is_v3:
            try:
                result = self.ocr.ocr(img_array, cls=True)
            except Exception as exc:
                errors.append(f"ocr(img, cls=True): {exc}")
                try:
                    result = self.ocr.ocr(image=img_array, cls=True)
                except Exception as exc2:
                    errors.append(f"ocr(image=..., cls=True): {exc2}")

        # Fallback for non-v3 wrappers that still expose predict.
        if result is None and not self.is_v3 and hasattr(self.ocr, "predict"):
            try:
                result = self.ocr.predict(img_array)
            except Exception as exc:
                errors.append(f"predict(img) fallback: {exc}")

        if result is None:
            raise RuntimeError("PaddleOCR inference failed: " + " | ".join(errors[-3:]))

        lines = self._extract_lines(result)
        return lines

    def _extract_lines(self, data: Any) -> List[str]:
        lines: List[str] = []

        def add_text(text: Any) -> None:
            # Accept only real text values, never geometry arrays.
            if not isinstance(text, str):
                return
            s = text.strip()
            if s:
                lines.append(s)

        if data is None:
            return lines

        if isinstance(data, str):
            add_text(data)
            return lines

        if isinstance(data, dict):
            if "rec_texts" in data and isinstance(data["rec_texts"], list):
                for text in data["rec_texts"]:
                    add_text(text)
            elif "texts" in data and isinstance(data["texts"], list):
                for text in data["texts"]:
                    add_text(text)
            elif "text" in data:
                add_text(data["text"])
            elif "rec_text" in data:
                add_text(data["rec_text"])
            else:
                # Legacy item shape: [ [x1,y1], [x2,y2], ... ], [text, score]
                for value in data.values():
                    lines.extend(self._extract_lines(value))
            return lines

        if isinstance(data, (list, tuple)):
            for item in data:
                # Legacy block shape
                if isinstance(item, (list, tuple)) and len(item) >= 2:
                    second = item[1]
                    if isinstance(second, str):
                        add_text(second)
                        continue
                    if (
                        isinstance(second, (list, tuple))
                        and len(second) >= 1
                        and isinstance(second[0], str)
                    ):
                        add_text(second[0])
                        continue
                lines.extend(self._extract_lines(item))
            return lines

        if hasattr(data, "to_dict"):
            try:
                return self._extract_lines(data.to_dict())
            except Exception:
                pass

        if hasattr(data, "__dict__"):
            try:
                return self._extract_lines(vars(data))
            except Exception:
                pass

        return lines
