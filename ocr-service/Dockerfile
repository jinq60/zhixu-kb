# =====================================================================
# 知序智能知识库 - OCR 服务镜像（Flask + RapidOCR / onnxruntime）
# 说明：容器内默认使用 RapidOCR（纯 CPU、onnxruntime 后端），
#       避免 paddlepaddle 在 WSL2/Docker 虚拟化下的 SIGILL 兼容问题。
#       如需 PaddleOCR 路线，可自行改用 requirements-paddle-legacy.txt。
# =====================================================================
FROM python:3.10-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1

WORKDIR /app

# RapidOCR / onnxruntime 运行依赖（OpenMP）
RUN apt-get update \
    && apt-get install -y --no-install-recommends libgomp1 libglib2.0-0 libgl1 curl \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt .
# 国内源加速；如需默认 PyPI 可去掉 -i 参数
RUN pip install --no-cache-dir -r requirements.txt -i https://pypi.tuna.tsinghua.edu.cn/simple

COPY . .

RUN mkdir -p tmp/ocr && \
    groupadd -r app && useradd -r -g app -d /app -s /sbin/nologin app && \
    chown -R app:app /app

USER app

EXPOSE 5001

HEALTHCHECK --interval=15s --timeout=5s --start-period=30s --retries=5 \
    CMD curl -fs http://localhost:5001/ocr/health || exit 1

CMD ["gunicorn", "-w", "2", "-b", "0.0.0.0:5001", "--timeout", "120", "--access-logfile", "-", "--error-logfile", "-", "app:app"]
