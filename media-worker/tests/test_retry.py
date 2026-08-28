from datetime import datetime, timezone
from pathlib import Path
from unittest.mock import Mock
from uuid import uuid4

import pika

from clipador_worker.config import Settings
from clipador_worker.contracts import MediaValidationCommandV1
from clipador_worker.rabbit import RETRY_EXCHANGE, RETRY_HEADER, RabbitMediaWorker, retry_count


def test_retry_count_handles_amqp_header_types() -> None:
    assert retry_count({RETRY_HEADER: 3}) == 3
    assert retry_count({RETRY_HEADER: "4"}) == 4
    assert retry_count({RETRY_HEADER: -1}) == 0
    assert retry_count({RETRY_HEADER: "invalid"}) == 0
    assert retry_count(None) == 0


def test_transient_failure_is_confirmed_to_retry_queue_before_ack(tmp_path: Path) -> None:
    worker = RabbitMediaWorker(Settings(
        rabbit_host="rabbit", rabbit_port=5672, rabbit_user="user", rabbit_password="password",
        storage_root=tmp_path, inbox_path=tmp_path / "inbox.sqlite3", max_retries=5,
        retry_delay_ms=30000, reconnect_max_seconds=30,
    ))
    command = MediaValidationCommandV1(
        schemaVersion=1, messageId=uuid4(), taskType="VALIDATE_MEDIA", jobId=uuid4(),
        videoId=uuid4(), correlationId="correlation", storageKey="videos/id/original.mp4",
        attempt=1, createdAt=datetime.now(timezone.utc),
    )
    channel = Mock()
    channel.basic_publish.return_value = True

    outcome = worker._retry_or_fail(
        channel, 7, pika.BasicProperties(headers={}), b"{}", command, RuntimeError("offline")
    )

    assert outcome == "retry"
    assert channel.basic_publish.call_args.kwargs["exchange"] == RETRY_EXCHANGE
    channel.basic_ack.assert_called_once_with(7)
    worker.stop()


def test_retry_exhaustion_publishes_failure_and_dead_letters_original(tmp_path: Path) -> None:
    worker = RabbitMediaWorker(Settings(
        rabbit_host="rabbit", rabbit_port=5672, rabbit_user="user", rabbit_password="password",
        storage_root=tmp_path, inbox_path=tmp_path / "inbox.sqlite3", max_retries=2,
        retry_delay_ms=30000, reconnect_max_seconds=30,
    ))
    command = MediaValidationCommandV1(
        schemaVersion=1, messageId=uuid4(), taskType="VALIDATE_MEDIA", jobId=uuid4(),
        videoId=uuid4(), correlationId="correlation", storageKey="videos/id/original.mp4",
        attempt=1, createdAt=datetime.now(timezone.utc),
    )
    channel = Mock()
    channel.basic_publish.return_value = True

    outcome = worker._retry_or_fail(
        channel, 9, pika.BasicProperties(headers={RETRY_HEADER: 2}), b"{}", command,
        RuntimeError("still offline"),
    )

    assert outcome == "failed"
    channel.basic_reject.assert_called_once_with(9, requeue=False)
    assert worker.health.processed == 1
    worker.stop()
