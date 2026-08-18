import logging
from pathlib import Path
import re
import tempfile
from typing import List

from PIL import Image

from config import settings

logger = logging.getLogger(__name__)

try:
    import torch
    from transformers import AutoModel, AutoTokenizer
    TRANSFORMERS_AVAILABLE = True
except ImportError:
    TRANSFORMERS_AVAILABLE = False
    logger.warning("transformers or torch not installed; DeepSeek-OCR will not be available.")


class DeepSeekOCRService:
    """DeepSeek-OCR-2 service using the open-source model from Hugging Face."""

    def __init__(self):
        self.available = False
        self.model = None
        self.tokenizer = None
        self.unavailable_reason = ""

        if not TRANSFORMERS_AVAILABLE:
            self.unavailable_reason = "DeepSeek-OCR dependencies missing: install torch and transformers"
            logger.warning(self.unavailable_reason)
            return

        # Current DeepSeek-OCR-2 infer path uses CUDA tensors internally.
        if not torch.cuda.is_available():
            self.unavailable_reason = (
                "DeepSeek-OCR-2 requires CUDA runtime for inference; "
                "install CUDA-enabled PyTorch and ensure NVIDIA GPU is available, "
                "or set USE_DEEPSEEK_OCR=false to use PaddleOCR"
            )
            logger.warning(self.unavailable_reason)
            return

        try:
            model_name = settings.DEEPSEEK_OCR_MODEL_NAME
            logger.info(f"Loading DeepSeek-OCR model: {model_name}")
            self._patch_known_model_issue(model_name)

            # Load model and tokenizer
            self.tokenizer = AutoTokenizer.from_pretrained(
                model_name,
                trust_remote_code=True,
                cache_dir=settings.MODEL_CACHE_DIR
            )

            self.model = AutoModel.from_pretrained(
                model_name,
                trust_remote_code=True,
                use_safetensors=True,
                _attn_implementation="eager",
                cache_dir=settings.MODEL_CACHE_DIR
            )

            # Runtime placement
            self.model = self.model.eval()
            self.model = self.model.cuda().to(torch.bfloat16 if settings.USE_BF16 else torch.float16)

            self.available = True
            self.unavailable_reason = ""
            logger.info("DeepSeek-OCR model loaded successfully")

        except Exception as e:
            logger.error(f"Failed to load DeepSeek-OCR model: {e}")
            self.unavailable_reason = f"DeepSeek-OCR model load failed: {e}"
            self.available = False

    def _patch_known_model_issue(self, model_name: str) -> None:
        """
        Patch a known upstream issue in DeepSeek-OCR-2 model code where
        `from addict import Dict` shadows typing.Dict and breaks type annotations
        on Python 3.8 with: TypeError("'type' object is not subscriptable").
        """
        candidates = []

        model_dir = Path(model_name)
        if model_dir.exists():
            candidates.append(model_dir / "modeling_deepseekocr2.py")

        hf_module_cache = Path.home() / ".cache" / "huggingface" / "modules" / "transformers_modules"
        if hf_module_cache.exists():
            candidates.extend(hf_module_cache.rglob("modeling_deepseekocr2.py"))

        for file_path in candidates:
            if not file_path.exists():
                continue
            try:
                text = file_path.read_text(encoding="utf-8")
                patched = text

                # Ensure typing import includes Dict exactly once.
                patched = patched.replace(
                    "from typing import List, Optional, Tuple, Union",
                    "from typing import List, Optional, Tuple, Union, Dict",
                )
                patched = patched.replace(
                    "from typing import List, Optional, Tuple, Union, Dict, Dict",
                    "from typing import List, Optional, Tuple, Union, Dict",
                )

                # Normalize addict import to a stable alias (idempotent).
                patched = re.sub(
                    r"from addict import Dict(?:\s+as\s+AddictDict)*",
                    "from addict import Dict as AddictDict",
                    patched,
                )

                # Keep runtime Dict() constructor from addict, and annotations from typing.Dict.
                patched = patched.replace("MlpProjector(Dict(", "MlpProjector(AddictDict(")

                if patched != text:
                    file_path.write_text(patched, encoding="utf-8")
                    logger.info(f"Patched DeepSeek model file: {file_path}")
            except Exception as exc:
                logger.warning(f"Failed patching {file_path}: {exc}")

    def is_ready(self) -> bool:
        return self.available

    def recognize(self, image: Image.Image) -> List[str]:
        if not self.available or self.model is None:
            raise RuntimeError("DeepSeek-OCR not available: model not loaded")

        try:
            # Convert PIL Image to format expected by model
            # DeepSeek-OCR-2 expects images in RGB format
            if image.mode != "RGB":
                image = image.convert("RGB")

            # DeepSeek-OCR-2 official inference entrypoint is model.infer(...)
            with tempfile.TemporaryDirectory(prefix="deepseek_ocr_") as tmp_dir:
                image_path = Path(tmp_dir) / "input.png"
                output_dir = Path(tmp_dir) / "out"
                output_dir.mkdir(parents=True, exist_ok=True)
                image.save(image_path)

                result = self.model.infer(
                    self.tokenizer,
                    prompt="<image>\nFree OCR.",
                    image_file=str(image_path),
                    output_path=str(output_dir),
                    base_size=1024,
                    image_size=768,
                    crop_mode=True,
                    save_results=False,
                )

            if isinstance(result, str):
                generated_text = result
            elif isinstance(result, dict):
                generated_text = (
                    result.get("text")
                    or result.get("result")
                    or result.get("content")
                    or str(result)
                )
            else:
                generated_text = str(result)

            # Split by lines and clean up
            lines = [line.strip() for line in generated_text.split("\n") if line.strip()]

            return lines

        except Exception as e:
            logger.error(f"DeepSeek-OCR recognition failed: {e}")
            raise RuntimeError(f"OCR recognition failed: {e}")
