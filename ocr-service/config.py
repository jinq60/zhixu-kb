import os
from pathlib import Path


_BASE_DIR = Path(__file__).resolve().parent


def _resolve_local_path(raw: str) -> str:
    path = Path(raw)
    if path.is_absolute():
        return str(path)
    return str((_BASE_DIR / path).resolve())


def _resolve_optional_local_path(raw: str) -> str:
    if not raw:
        return ""
    return _resolve_local_path(raw)


def _resolve_model_ref(raw: str) -> str:
    """
    Keep Hugging Face repo IDs untouched, but resolve local relative paths
    against the LNO-OCR directory so startup path does not matter.
    """
    path = Path(raw)
    if path.is_absolute():
        return str(path)

    candidate = (_BASE_DIR / path).resolve()
    if raw.startswith(".") or candidate.exists():
        return str(candidate)
    return raw


class Settings:
    """Runtime settings for the OCR service."""

    HOST: str = os.getenv("OCR_HOST", "0.0.0.0")
    PORT: int = int(os.getenv("OCR_PORT", "5001"))
    DEBUG: bool = os.getenv("OCR_DEBUG", "false").lower() == "true"
    CORS_ENABLED: bool = os.getenv("OCR_CORS", "true").lower() == "true"
    MAX_CONTENT_LENGTH: int = int(os.getenv("OCR_MAX_CONTENT_MB", "15")) * 1024 * 1024
    REQUEST_TIMEOUT: int = int(os.getenv("OCR_REQUEST_TIMEOUT", "60"))

    # DeepSeek-OCR-2 Open Source Model Configuration
    # Safe default is PaddleOCR; enable DeepSeek explicitly in .env when CUDA is ready.
    USE_DEEPSEEK: bool = os.getenv("USE_DEEPSEEK_OCR", "false").lower() == "true"
    DEEPSEEK_OCR_MODEL_NAME: str = _resolve_model_ref(os.getenv("DEEPSEEK_OCR_MODEL", "deepseek-ai/DeepSeek-OCR-2"))
    MODEL_CACHE_DIR: str = _resolve_local_path(os.getenv("MODEL_CACHE_DIR", "./models"))
    USE_GPU: bool = os.getenv("USE_GPU", "false").lower() == "true"
    USE_BF16: bool = os.getenv("USE_BF16", "true").lower() == "true"
    MAX_NEW_TOKENS: int = int(os.getenv("MAX_NEW_TOKENS", "2048"))

    # Legacy PaddleOCR Configuration (fallback)
    OCR_LANG: str = os.getenv("OCR_LANG", "ch")
    OCR_USE_ANGLE: bool = os.getenv("OCR_USE_ANGLE", "true").lower() == "true"
    OCR_USE_GPU: bool = os.getenv("OCR_USE_GPU", "false").lower() == "true"
    PADDLE_DET_MODEL_DIR: str = _resolve_optional_local_path(os.getenv("PADDLE_DET_MODEL_DIR", ""))
    PADDLE_REC_MODEL_DIR: str = _resolve_optional_local_path(os.getenv("PADDLE_REC_MODEL_DIR", ""))
    PADDLE_CLS_MODEL_DIR: str = _resolve_optional_local_path(os.getenv("PADDLE_CLS_MODEL_DIR", ""))
    TMP_DIR: str = _resolve_local_path(os.getenv("OCR_TMP_DIR", "./tmp/ocr"))

    # Multi-channel routing:
    # - OCR_DEFAULT_ENGINE: auto | paddle | deepseek
    # - OCR_FALLBACK_ENGINES: comma-separated sequence used when default fails in auto mode
    # Backward compatibility:
    # - if USE_DEEPSEEK_OCR=true and OCR_DEFAULT_ENGINE not set, default engine becomes deepseek.
    OCR_ENABLE_PADDLE: bool = os.getenv("OCR_ENABLE_PADDLE", "true").lower() == "true"
    OCR_ENABLE_DEEPSEEK: bool = os.getenv("OCR_ENABLE_DEEPSEEK", "true" if USE_DEEPSEEK else "false").lower() == "true"
    OCR_DEFAULT_ENGINE: str = os.getenv(
        "OCR_DEFAULT_ENGINE",
        "deepseek" if USE_DEEPSEEK else "paddle",
    ).strip().lower()
    OCR_FALLBACK_ENGINES: list[str] = [
        e.strip().lower()
        for e in os.getenv("OCR_FALLBACK_ENGINES", "paddle,deepseek").split(",")
        if e.strip()
    ]


settings = Settings()
