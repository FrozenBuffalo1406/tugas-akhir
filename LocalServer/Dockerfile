# Base image Python 3.11 (Komennya udah dibenerin)
FROM python:3.11-slim

# [TAMBAHAN] Set ENV PYTHONUNBUFFERED=1 biar log Python langsung keluar
ENV PYTHONUNBUFFERED=1
WORKDIR /app
RUN useradd -m -s /bin/bash appuser
COPY backend/requirements.txt .
RUN apt-get update && \
    # Install build-essential
    apt-get install -y --no-install-recommends build-essential && \
    # Upgrade pip
    pip install --upgrade pip && \
    # Install library (timeout udah bagus)
    pip install --no-cache-dir --default-timeout=100 -r requirements.txt && \
    # [TAMBAHAN] Cleanup (Hapus build-essential) biar image ramping
    apt-get purge -y --auto-remove build-essential && \
    # [JAWABAN PERTANYAANMU] Cleanup cache apt-get
    rm -rf /var/lib/apt/lists/*
# Copy seluruh folder backend ke kontainer
COPY ./backend .
RUN chown -R appuser:appuser /app
USER appuser
# Ekspos port yang akan digunakan Gunicorn di dalam kontainer
EXPOSE 8000
# Perintah untuk menjalankan Gunicorn (tetep sama, udah bagus)
CMD ["gunicorn", "--workers", "1", "--bind", "0.0.0.0:8000", "--log-level", "info", "--access-logfile", "-", "--error-logfile", "-", "--preload", "--timeout", "300", "app:app"]