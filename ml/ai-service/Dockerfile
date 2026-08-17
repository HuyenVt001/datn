# AI service (FastAPI) — deploy chinh: Google Cloud Run (free tier).
# Chay duoc y het tren HF Space (Docker SDK) / Render / bat ky noi nao nhan Dockerfile:
# doc cong tu bien PORT, mac dinh 8080 (Cloud Run inject PORT; HF Space dat PORT=7860).
FROM python:3.11-slim

RUN apt-get update && apt-get install -y --no-install-recommends libgomp1 && rm -rf /var/lib/apt/lists/*

# Chay bang user thuong (HF Spaces bat buoc uid 1000; Cloud Run khong quan tam)
RUN useradd -m -u 1000 user
ENV HOME=/home/user \
    PATH=/home/user/.local/bin:$PATH \
    HF_HOME=/home/user/.cache/huggingface \
    PORT=8080 \
    PYTHONUNBUFFERED=1
WORKDIR /home/user/app

COPY --chown=user requirements.txt .
RUN pip install --no-cache-dir --upgrade pip && pip install --no-cache-dir -r requirements.txt

COPY --chown=user . .
USER user

EXPOSE 8080
# Shell form de $PORT duoc expand luc chay (Cloud Run doi cong tuy y)
CMD exec uvicorn app:app --host 0.0.0.0 --port ${PORT:-8080} --workers 1
