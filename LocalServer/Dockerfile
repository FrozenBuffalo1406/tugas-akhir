FROM python:3.11-slim
ENV PYTHONUNBUFFERED=1
WORKDIR /app
RUN useradd -m -s /bin/bash appuser
COPY backend/requirements.txt .
RUN apt-get update && \
    apt-get install -y --no-install-recommends build-essential && \
    pip install --upgrade pip && \
    pip install --no-cache-dir --default-timeout=100 -r requirements.txt && \
    apt-get purge -y --auto-remove build-essential && \
    rm -rf /var/lib/apt/lists/*
COPY ./backend .
RUN chown -R appuser:appuser /app
USER appuser
EXPOSE 8000
CMD ["gunicorn", "--workers", "4", "--bind", "0.0.0.0:8000", \
     "--log-level", "info", "--access-logfile", "-", "--error-logfile", "-", \
     "--preload", \
     "app:app"]