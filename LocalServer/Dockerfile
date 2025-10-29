# Base image Python 3.8
FROM python:3.8-slim

# Set working directory di dalam kontainer
WORKDIR /app

# Install dependensi sistem jika perlu (misal: build-essential kalau ada library C)
# RUN apt-get update && apt-get install -y --no-install-recommends build-essential && rm -rf /var/lib/apt/lists/*

# Copy requirements dulu untuk optimasi cache Docker
COPY backend/requirements.txt .

# Install library Python
# Gunakan --default-timeout=100 untuk koneksi lambat
RUN pip install --no-cache-dir --default-timeout=100 -r requirements.txt

# Copy seluruh folder backend ke kontainer
COPY ./backend .

# Buat direktori untuk database dan log di dalam kontainer (opsional, bagus jika tidak pakai volume)
# RUN mkdir data logs

# Ekspos port yang akan digunakan Gunicorn di dalam kontainer
EXPOSE 8000

# Perintah untuk menjalankan Gunicorn
# --log-level info: agar log Gunicorn juga tampil
# --access-logfile - : kirim access log ke stdout (biar bisa diliat `docker logs`)
# --error-logfile - : kirim error log ke stdout
CMD ["gunicorn", "--workers", "3", "--bind", "0.0.0.0:8000", "--log-level", "info", "--access-logfile", "-", "--error-logfile", "-", "app:app"]
