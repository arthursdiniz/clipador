FROM python:3.13.14-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1

RUN apt-get update \
    && apt-get install --no-install-recommends -y ffmpeg \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 10001 --create-home worker
WORKDIR /app
COPY pyproject.toml README.md ./
COPY src ./src
RUN python -m pip install --upgrade pip==25.2 \
    && python -m pip install . \
    && mkdir -p /var/lib/clipador/storage /var/lib/clipador-worker/state /var/lib/clipador-worker/models \
    && chown -R worker:worker /app /var/lib/clipador-worker /var/lib/clipador/storage

USER 10001
EXPOSE 8090
CMD ["uvicorn", "clipador_worker.app:app", "--host", "0.0.0.0", "--port", "8090"]
