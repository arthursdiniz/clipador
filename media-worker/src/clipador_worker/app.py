from __future__ import annotations

import threading
from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.responses import JSONResponse, PlainTextResponse

from .config import Settings
from .observability import configure_json_logging
from .rabbit import RabbitMediaWorker

configure_json_logging()

worker: RabbitMediaWorker | None = None
worker_thread: threading.Thread | None = None


@asynccontextmanager
async def lifespan(_: FastAPI):
    global worker, worker_thread
    worker = RabbitMediaWorker(Settings.from_env())
    worker_thread = threading.Thread(target=worker.run_forever, name="rabbit-consumer", daemon=True)
    worker_thread.start()
    try:
        yield
    finally:
        worker.stop()
        worker_thread.join(timeout=10)


app = FastAPI(title="Clipador Media Worker", version="0.1.0", lifespan=lifespan)


@app.get("/health", include_in_schema=False)
def health() -> JSONResponse:
    if worker is None:
        return JSONResponse(status_code=503, content={"status": "starting"})
    content: dict[str, object] = {
        "status": "up" if worker.health.connected else "degraded",
        "rabbitConnected": worker.health.connected,
        "processed": worker.health.processed,
    }
    if worker.health.last_error:
        content["lastError"] = worker.health.last_error
    return JSONResponse(status_code=200 if worker.health.connected else 503, content=content)


@app.get("/metrics", include_in_schema=False)
def metrics() -> PlainTextResponse:
    if worker is None:
        return PlainTextResponse("clipador_worker_connected 0\n",
                                 media_type="text/plain; version=0.0.4; charset=utf-8")
    return PlainTextResponse(worker.metrics.render_prometheus(worker.health.connected),
                             media_type="text/plain; version=0.0.4; charset=utf-8")
