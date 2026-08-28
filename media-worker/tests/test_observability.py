import json
import logging

from clipador_worker.observability import JsonLogFormatter, WorkerMetrics


def test_worker_metrics_are_prometheus_compatible_and_partitioned_by_outcome() -> None:
    metrics = WorkerMetrics()
    metrics.observe_task("TRANSCRIBE_AUDIO", "succeeded", 2.5)
    metrics.observe_task("TRANSCRIBE_AUDIO", "retry", 0.5)

    output = metrics.render_prometheus(True)

    assert "clipador_worker_connected 1" in output
    assert 'clipador_worker_tasks_total{task="transcribe_audio",outcome="succeeded"} 1' in output
    assert 'clipador_worker_tasks_total{task="transcribe_audio",outcome="retry"} 1' in output
    assert 'clipador_worker_task_duration_seconds_count{task="transcribe_audio"} 2' in output
    assert 'clipador_worker_task_duration_seconds_sum{task="transcribe_audio"} 3.000000' in output


def test_json_log_formatter_preserves_correlation_fields() -> None:
    record = logging.LogRecord("worker", logging.INFO, __file__, 1, "completed", (), None)
    record.correlationId = "correlation-1"
    record.jobId = "job-1"

    payload = json.loads(JsonLogFormatter().format(record))

    assert payload["message"] == "completed"
    assert payload["correlationId"] == "correlation-1"
    assert payload["jobId"] == "job-1"
