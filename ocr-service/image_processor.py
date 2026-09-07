import io
import logging
from typing import Optional, Tuple

import numpy as np
from PIL import Image, ImageFilter, ImageOps

logger = logging.getLogger(__name__)

# P0-8 修复：解压炸弹防护（全解码前拦截超大像素图）
Image.MAX_IMAGE_PIXELS = 50_000_000  # ~50MP
Image.LOAD_TRUNCATED_IMAGES = False
_ALLOWED_FORMATS = {"JPEG", "PNG", "WEBP", "BMP", "TIFF"}

# ---------------------------------------------------------------------------
# Optional OpenCV import – gracefully degrade when not installed.
# ---------------------------------------------------------------------------
try:
    import cv2

    _HAS_CV2 = True
except ImportError:
    _HAS_CV2 = False


def preprocess(image: Image.Image) -> Image.Image:
    """
    Basic preprocessing: grayscale -> denoise -> adaptive threshold.
    Keeps it lightweight and dependency friendly.
    """
    gray = ImageOps.grayscale(image)
    denoised = gray.filter(ImageFilter.MedianFilter(size=3))
    # Convert to black/white with adaptive threshold-like effect
    bw = denoised.point(lambda x: 0 if x < 160 else 255, "1")
    return bw.convert("RGB")


def auto_crop_and_correct(image: Image.Image) -> Image.Image:
    """
    Detect the largest rectangular contour (paper / note boundary) in the
    image and apply a perspective transform to produce a top-down view.

    Falls back to the original image when OpenCV is unavailable or no
    suitable contour is found.
    """
    if not _HAS_CV2:
        logger.debug("OpenCV not available, skipping auto-crop & perspective correction")
        return image

    try:
        img_array = np.array(image)
        if img_array.ndim == 2:
            gray = img_array
        else:
            gray = cv2.cvtColor(img_array, cv2.COLOR_RGB2GRAY)

        # Blur + edge detection
        blurred = cv2.GaussianBlur(gray, (5, 5), 0)
        edged = cv2.Canny(blurred, 50, 200)

        # Dilate to close small gaps in the edges
        kernel = cv2.getStructuringElement(cv2.MORPH_RECT, (3, 3))
        edged = cv2.dilate(edged, kernel, iterations=1)

        contours, _ = cv2.findContours(edged, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
        if not contours:
            return image

        # Sort contours by area descending, pick largest candidates
        contours = sorted(contours, key=cv2.contourArea, reverse=True)[:5]

        doc_contour = None
        for cnt in contours:
            peri = cv2.arcLength(cnt, True)
            approx = cv2.approxPolyDP(cnt, 0.02 * peri, True)
            if len(approx) == 4:
                # Require the detected rectangle covers at least 10% of image area
                img_area = gray.shape[0] * gray.shape[1]
                if cv2.contourArea(approx) > img_area * 0.1:
                    doc_contour = approx
                    break

        if doc_contour is None:
            logger.debug("No rectangular contour found, skipping perspective correction")
            return image

        return _four_point_transform(img_array, doc_contour.reshape(4, 2))
    except Exception:
        logger.exception("Auto-crop / perspective correction failed, returning original")
        return image


def _order_points(pts: np.ndarray) -> np.ndarray:
    """Order four points as: top-left, top-right, bottom-right, bottom-left."""
    rect = np.zeros((4, 2), dtype="float32")
    s = pts.sum(axis=1)
    rect[0] = pts[np.argmin(s)]
    rect[2] = pts[np.argmax(s)]
    d = np.diff(pts, axis=1)
    rect[1] = pts[np.argmin(d)]
    rect[3] = pts[np.argmax(d)]
    return rect


def _four_point_transform(image: np.ndarray, pts: np.ndarray) -> Image.Image:
    """Apply perspective warp to obtain a top-down view of the region."""
    rect = _order_points(pts.astype("float32"))
    (tl, tr, br, bl) = rect

    width_a = np.linalg.norm(br - bl)
    width_b = np.linalg.norm(tr - tl)
    max_width = max(int(width_a), int(width_b))

    height_a = np.linalg.norm(tr - br)
    height_b = np.linalg.norm(tl - bl)
    max_height = max(int(height_a), int(height_b))

    dst = np.array([
        [0, 0],
        [max_width - 1, 0],
        [max_width - 1, max_height - 1],
        [0, max_height - 1]
    ], dtype="float32")

    matrix = cv2.getPerspectiveTransform(rect, dst)
    warped = cv2.warpPerspective(image, matrix, (max_width, max_height))
    return Image.fromarray(warped)


def load_image_from_bytes(data: bytes) -> Image.Image:
    # P0-8 修复：格式白名单 + 像素上限在 open 时即校验（Pillow 在 load 时触发 DecompressionBombWarning/Error）
    with Image.open(io.BytesIO(data)) as img:
        fmt = (img.format or "").upper()
        if fmt and fmt not in _ALLOWED_FORMATS:
            raise ValueError(f"Unsupported image format: {fmt}")
        img.load()
        # 缩小后再转 RGB，减少大图内存占用
        if img.width * img.height > Image.MAX_IMAGE_PIXELS:
            raise ValueError("Image too large")
        return img.convert("RGB")


def resize_if_needed(img: Image.Image, max_size: Tuple[int, int] = (2000, 2000)) -> Image.Image:
    """Resize large images to speed up OCR while keeping readability."""
    if img.width <= max_size[0] and img.height <= max_size[1]:
        return img
    img.thumbnail(max_size, Image.LANCZOS)
    return img


def to_bytes(img: Image.Image) -> bytes:
    buf = io.BytesIO()
    img.save(buf, format="PNG")
    return buf.getvalue()
