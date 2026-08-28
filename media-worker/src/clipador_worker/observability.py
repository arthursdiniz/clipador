from __future__ import annotations

import json
import logging
import threading
from collections import defaultdict
from datetime import UTC, datetime


class JsonLogFormatter(logging.Formatter):
    def format(self, record: logging.LogRecord) -> str:
        payload: dict[str, object] = {
            "timestamp": datetime.now(UTC).isoformat(),
            "level": record.levelname,
            "logger": record.name,
            "message": record.getMessage(),
        }
        for key in ("correlationId", "jobId", "videoId", "messageId", "taskType"):
            value = getattr(record, key, None)
            if value is not None:
                payload[key] = str(value)
        if record.exc_info:
            payload["exception"] = self.formatException(record.exc_info)[:4000]
        return json.dumps(payload, ensure_ascii=False)


def configure_json_logging() -> None:
    handler = logging.StreamHandler()
    handler.setFormatter(JsonLogFormatter())
    root = logging.getLogger()
    root.handlers.clear()
    root.addHandler(handler)
    root.setLevel(logging.INFO)


class WorkerMetrics:
    def __init__(self) -> None:
        self._lock = threading.Lock()
        self._tasks: defaultdict[tuple[str, str], int] = defaultdict(int)
        self._duration_count: defaultdict[str, int] = defaultdict(int)
        self._duration_sum: defaultdict[str, float] = defaultdict(float)

    def observe_task(self, task: str, outcome: str, duration_seconds: float) -> None:
        normalized_task = _label(task)
        normalized_outcome = _label(outcome)
        with self._lock:
            self._tasks[(normalized_task, normalized_outcome)] += 1
            self._duration_count[normalized_task] += 1
            self._duration_sum[normalized_task] += max(0.0, duration_seconds)

    def render_prometheus(self, connected: bool) -> str:
        with self._lock:
            tasks = sorted(self._tasks.items())
            counts = dict(self._duration_count)
            sums = dict(self._duration_sum)
        lines = [
            "# HELP clipador_worker_connected Whether the worker is connected to RabbitMQ.",
            "# TYPE clipador_worker_connected gauge",
            f"clipador_worker_connected {1 if connected else 0}",
            "# HELP clipador_worker_tasks_total Media tasks handled by task and outcome.",
            "# TYPE clipador_worker_tasks_total counter",
        ]
        lines.extend(
            f'clipador_worker_tasks_total{{task="{task}",outcome="{outcome}"}} {value}'
            for (task, outcome), value in tasks
        )
        lines.extend([
            "# HELP clipador_worker_task_duration_seconds Time spent handling media tasks.",
            "# TYPE clipador_worker_task_duration_seconds summary",
        ])
        for task in sorted(counts):
            lines.append(
                f'clipador_worker_task_duration_seconds_count{{task="{task}"}} {counts[task]}')
            lines.append(
                f'clipador_worker_task_duration_seconds_sum{{task="{task}"}} {sums[task]:.6f}')
        return "\n".join(lines) + "\n"


def _label(value: str) -> str:
    return "".join(character.lower() if character.isalnum() else "_" for character in value)[:64]
