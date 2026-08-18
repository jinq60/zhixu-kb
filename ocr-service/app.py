import logging
import os
from typing import Any, Dict, List, Optional

import requests
from flask import Flask, jsonify, request
from PIL import Image

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s - %(message)s")
logger = logging.getLogger(__name__)

try:
    from dotenv import load_dotenv

    base_dir = os.path.dirname(os.path.abspath(__file__))
    load_dotenv(dotenv_path=os.path.join(base_dir, ".env"), override=False)
except Exception:
    pass

from config import settings
from image_processor import load_image_from_bytes, auto_crop_and_correct

app = Flask(__name__)
app.config["MAX_CONTENT_LENGTH"] = settings.MAX_CONTENT_LENGTH

if settings.CORS_ENABLED:
    try:
        from flask_cors import CORS

        CORS(app)
    except Exception:
        logger.warning("flask_cors not installed, CORS disabled.")


def _init_paddle_service():
    if not settings.OCR_ENABLE_PADDLE:
        logger.info("Paddle engine disabled by OCR_ENABLE_PADDLE=false")
        return None
    try:
        from ocr_service import OCRService

        svc = OCRService(
            lang=settings.OCR_LANG,
            use_angle=settings.OCR_USE_ANGLE,
            use_gpu=settings.OCR_USE_GPU,
        )
        logger.info("Paddle OCR service initialized, ready=%s", svc.is_ready())
        return svc
    except Exception as exc:
        logger.exception("Initialize Paddle OCR service failed: %s", exc)
        return None


def _init_deepseek_service():
    if not settings.OCR_ENABLE_DEEPSEEK:
        logger.info("DeepSeek engine disabled by OCR_ENABLE_DEEPSEEK=false")
        return None
    try:
        from deepseek_ocr_service import DeepSeekOCRService

        svc = DeepSeekOCRService()
        logger.info("DeepSeek OCR service initialized, ready=%s", svc.is_ready())
        return svc
    except Exception as exc:
        logger.exception("Initialize DeepSeek OCR service failed: %s", exc)
        return None


OCR_ENGINES: Dict[str, Any] = {
    "paddle": _init_paddle_service(),
    "deepseek": _init_deepseek_service(),
}


def _engine_state(name: str) -> Dict[str, Any]:
    service = OCR_ENGINES.get(name)
    if service is None:
        return {
            "enabled": False,
            "ready": False,
            "reason": "engine not initialized",
        }
    ready = bool(service.is_ready())
    reason = getattr(service, "unavailable_reason", "") if not ready else ""
    return {
        "enabled": True,
        "ready": ready,
        "reason": reason,
    }


def _normalize_engine(raw: Optional[str]) -> str:
    value = (raw or "").strip().lower()
    return value or "auto"


def _resolve_engine_chain(requested_engine: str) -> List[str]:
    requested_engine = _normalize_engine(requested_engine)
    valid = {"auto", "paddle", "deepseek"}
    if requested_engine not in valid:
        raise ValueError(f"Unsupported OCR engine: {requested_engine}")

    if requested_engine in {"paddle", "deepseek"}:
        return [requested_engine]

    chain: List[str] = []

    default_engine = _normalize_engine(settings.OCR_DEFAULT_ENGINE)
    if default_engine in {"paddle", "deepseek"}:
        chain.append(default_engine)

    for engine in settings.OCR_FALLBACK_ENGINES:
        if engine in {"paddle", "deepseek"}:
            chain.append(engine)

    # Ensure both known engines are considered in auto mode.
    chain.extend(["paddle", "deepseek"])

    # Deduplicate while preserving order.
    seen = set()
    deduped: List[str] = []
    for engine in chain:
        if engine not in seen:
            seen.add(engine)
            deduped.append(engine)

    return deduped


def _parse_payload() -> Dict[str, Any]:
    return request.get_json(silent=True) or {}


def _is_safe_url(url: str) -> bool:
    """Validate URL to prevent SSRF attacks."""
    from urllib.parse import urlparse
    import ipaddress

    try:
        parsed = urlparse(url)
    except Exception:
        return False

    if parsed.scheme not in ("http", "https"):
        return False

    hostname = parsed.hostname
    if not hostname:
        return False

    try:
        addr = ipaddress.ip_address(hostname)
        if addr.is_private or addr.is_loopback or addr.is_reserved or addr.is_link_local:
            return False
    except ValueError:
        # hostname is a domain name, resolve it
        import socket
        try:
            resolved = socket.getaddrinfo(hostname, None)
            for _, _, _, _, sockaddr in resolved:
                addr = ipaddress.ip_address(sockaddr[0])
                if addr.is_private or addr.is_loopback or addr.is_reserved or addr.is_link_local:
                    return False
        except socket.gaierror:
            return False

    return True


def fetch_image_bytes(payload: Dict[str, Any]) -> Optional[bytes]:
    if "file" in request.files:
        data = request.files["file"].read()
        return data if data else None
    if "image" in request.files:
        data = request.files["image"].read()
        return data if data else None

    image_url = payload.get("image_url")
    if image_url:
        if not _is_safe_url(image_url):
            raise ValueError("Blocked: URL points to a private or reserved address")
        resp = requests.get(image_url, timeout=settings.REQUEST_TIMEOUT)
        resp.raise_for_status()
        return resp.content
    return None


@app.route("/ocr/health", methods=["GET"])
def health():
    states = {name: _engine_state(name) for name in ["paddle", "deepseek"]}
    ready_any = any(s["ready"] for s in states.values())
    return jsonify(
        {
            "status": "ok" if ready_any else "degraded",
            "ocr_ready": ready_any,
            "default_engine": _normalize_engine(settings.OCR_DEFAULT_ENGINE),
            "engines": states,
        }
    )


@app.route("/ocr/engines", methods=["GET"])
def engines():
    states = {name: _engine_state(name) for name in ["paddle", "deepseek"]}
    return jsonify(
        {
            "default_engine": _normalize_engine(settings.OCR_DEFAULT_ENGINE),
            "fallback_engines": settings.OCR_FALLBACK_ENGINES,
            "engines": states,
        }
    )


@app.route("/ocr/recognize", methods=["POST"])
def recognize():
    payload = _parse_payload()
    requested_engine = _normalize_engine(
        request.args.get("engine")
        or request.form.get("engine")
        or payload.get("engine")
    )

    try:
        engine_chain = _resolve_engine_chain(requested_engine)
    except ValueError as exc:
        return jsonify({"error": str(exc)}), 400

    try:
        data = fetch_image_bytes(payload)
        if not data:
            return jsonify({"error": "No image provided (multipart `file` or JSON `image_url`)"}), 400

        image: Image.Image = load_image_from_bytes(data)

        # Auto-crop and perspective correction for photographed notes
        image = auto_crop_and_correct(image)

        errors: List[str] = []
        for engine in engine_chain:
            service = OCR_ENGINES.get(engine)
            if service is None:
                errors.append(f"{engine}: engine not initialized")
                continue

            if not service.is_ready():
                reason = getattr(service, "unavailable_reason", "not ready")
                errors.append(f"{engine}: {reason}")
                continue

            try:
                lines = service.recognize(image)
                joined = "\n".join(lines)
                return jsonify({"engine": engine, "lines": lines, "text": joined})
            except Exception as exc:
                errors.append(f"{engine}: {exc}")
                # For explicit engine selection, stop immediately.
                if requested_engine in {"paddle", "deepseek"}:
                    break

        return jsonify({"error": "No OCR engine could process request", "details": errors}), 503
    except ValueError as exc:
        return jsonify({"error": str(exc)}), 400
    except requests.RequestException as exc:
        logger.exception("Fetch image failed")
        return jsonify({"error": f"Fetch image failed: {exc}"}), 400
    except RuntimeError as exc:
        return jsonify({"error": str(exc)}), 503
    except Exception as exc:  # pragma: no cover
        logger.exception("OCR recognize failed")
        return jsonify({"error": str(exc)}), 500


if __name__ == "__main__":
    os.makedirs(settings.TMP_DIR, exist_ok=True)
    app.run(host=settings.HOST, port=settings.PORT, debug=settings.DEBUG)
