# Gunakan Python 3.11 Slim (Kompatibel sama TF 2.18)
FROM python:3.11-slim

# Set Environment Variables
ENV PYTHONUNBUFFERED=1
WORKDIR /app

# 1. Install System Dependencies
# libatlas-base-dev & gfortran wajib buat Scipy/Numpy biar gak error compile
RUN apt-get update && apt-get install -y --no-install-recommends \
    build-essential \
    libatlas-base-dev \
    gfortran \
    && rm -rf /var/lib/apt/lists/*

# 2. Buat User (Security Best Practice)
RUN useradd -m -s /bin/bash appuser

# 3. Copy requirements DULUAN (Layer Caching)
COPY requirements.txt .

# 4. Upgrade pip & wheel (Biar install lebih enteng)
RUN pip install --upgrade pip && pip install wheel

# --- [ZONA ANTI-CRASH MEMORY] ---
# Kita install satu-satu secara manual biar RAM VPS gak jebol.
# Versi disamakan PERSIS dengan request lu.

RUN pip install numpy==1.26.4
RUN pip install scipy
# Tensorflow paling berat, install sendirian
RUN pip install tensorflow==2.18.0

# 5. Install sisa library lainnya (Flask, dll)
# --no-deps biar gak reinstall numpy/tf lagi
RUN pip install --no-cache-dir --default-timeout=100 -r requirements.txt

# 6. Copy Seluruh Kodingan Backend
# Karena Dockerfile ada di dalam folder backend, pake COPY . .
COPY . .

# 7. Atur Permission ke appuser
RUN chown -R appuser:appuser /app

# 8. Ganti ke User Biasa
USER appuser

# 9. Expose Port 8080 (Sesuai app.py lu)
EXPOSE 8080

# 10. Command Gunicorn
# Gw set port 8080 biar konsisten sama app.py lu
# Workers gw set 3 (biar agak hemat RAM dibanding 4)
CMD ["gunicorn", "--workers", "3", "--bind", "0.0.0.0:8080", \
     "--log-level", "info", "--access-logfile", "-", "--error-logfile", "-", \
     "--timeout", "120", \
     "--preload", \
     "app:app"]