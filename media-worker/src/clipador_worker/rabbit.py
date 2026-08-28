from __future__ import annotations

import logging
import threading
import time
from dataclasses import dataclass
from uuid import UUID

import pika
from pydantic import ValidationError

from .config import Settings
from .artifacts import JobArtifactStorage
from .audio import FfmpegAudioExtractor, MediaTaskExecutionError
from .contracts import (ExtractAudioCommandV1, MediaCommand, MediaTaskResultV1,
                        MediaValidationCommandV1, TranscribeAudioCommandV1,
                        AnalyzeContentCommandV1, RenderClipsCommandV1,
                        RenderClipsCommandV2, parse_command)
from .analysis import LocalMultimodalClipAnalyzer, OllamaClipAnalysisProvider
from .inbox import InboxStore
from .observability import WorkerMetrics
from .storage import InvalidStorageKey, LocalMediaValidator, MediaTemporarilyUnavailable
from .transcription import FasterWhisperTranscriber
from .rendering import FfmpegClipRenderer

COMMAND_EXCHANGE = "clipador.commands.v1"
RESULT_EXCHANGE = "clipador.results.v1"
RETRY_EXCHANGE = "clipador.retry.v1"
DLX_EXCHANGE = "clipador.dlx.v1"
COMMAND_QUEUE = "clipador.media.validate.v1"
COMMAND_RETRY_QUEUE = "clipador.media.validate.retry.v1"
COMMAND_DLQ = "clipador.media.validate.dlq.v1"
EXTRACT_QUEUE = "clipador.media.extract-audio.v1"
EXTRACT_RETRY_QUEUE = "clipador.media.extract-audio.retry.v1"
EXTRACT_DLQ = "clipador.media.extract-audio.dlq.v1"
TRANSCRIBE_QUEUE = "clipador.media.transcribe.v1"
TRANSCRIBE_RETRY_QUEUE = "clipador.media.transcribe.retry.v1"
TRANSCRIBE_DLQ = "clipador.media.transcribe.dlq.v1"
ANALYZE_QUEUE = "clipador.media.analyze.v1"
ANALYZE_RETRY_QUEUE = "clipador.media.analyze.retry.v1"
ANALYZE_DLQ = "clipador.media.analyze.dlq.v1"
RENDER_QUEUE = "clipador.media.render.v1"
RENDER_RETRY_QUEUE = "clipador.media.render.retry.v1"
RENDER_DLQ = "clipador.media.render.dlq.v1"
RESULT_QUEUE = "clipador.backend.results.v1"
RESULT_RETRY_QUEUE = "clipador.backend.results.retry.v1"
RESULT_DLQ = "clipador.backend.results.dlq.v1"
RETRY_HEADER = "x-clipador-retry-count"

logger = logging.getLogger(__name__)


@dataclass
class WorkerHealth:
    connected: bool = False
    last_error: str | None = None
    processed: int = 0


class RabbitMediaWorker:
    def __init__(self, settings: Settings) -> None:
        self._settings = settings
        self._inbox = InboxStore(settings.inbox_path)
        self._validator = LocalMediaValidator(settings.storage_root)
        self._artifacts = JobArtifactStorage(settings.storage_root)
        self._audio_extractor = FfmpegAudioExtractor(settings, self._validator, self._artifacts)
        self._transcriber = FasterWhisperTranscriber(settings, self._artifacts)
        analyzer_type = (OllamaClipAnalysisProvider if settings.analysis_provider == "ollama"
                         else LocalMultimodalClipAnalyzer)
        self._analyzer = analyzer_type(settings, self._artifacts, self._validator)
        self._renderer = FfmpegClipRenderer(settings, self._artifacts, self._validator)
        self._stop = threading.Event()
        self._connection: pika.BlockingConnection | None = None
        self.health = WorkerHealth()
        self.metrics = WorkerMetrics()

    def run_forever(self) -> None:
        delay = 1
        while not self._stop.is_set():
            try:
                self._consume()
                delay = 1
            except Exception as exc:  # connection loop is the process supervision boundary
                self.health.connected = False
                self.health.last_error = str(exc)[:500]
                logger.exception("RabbitMQ consumer stopped; reconnecting in %s seconds", delay)
                self._stop.wait(delay)
                delay = min(delay * 2, self._settings.reconnect_max_seconds)

    def stop(self) -> None:
        self._stop.set()
        connection = self._connection
        if connection and connection.is_open:
            connection.add_callback_threadsafe(connection.close)
        self._inbox.close()

    def _consume(self) -> None:
        credentials = pika.PlainCredentials(self._settings.rabbit_user, self._settings.rabbit_password)
        parameters = pika.ConnectionParameters(
            host=self._settings.rabbit_host,
            port=self._settings.rabbit_port,
            credentials=credentials,
            heartbeat=60,
            blocked_connection_timeout=30,
            connection_attempts=1,
        )
        self._connection = pika.BlockingConnection(parameters)
        channel = self._connection.channel()
        self._declare_topology(channel)
        channel.confirm_delivery()
        channel.basic_qos(prefetch_count=1)
        task_queues = {
            "VALIDATE_MEDIA": COMMAND_QUEUE,
            "EXTRACT_AUDIO": EXTRACT_QUEUE,
            "TRANSCRIBE_AUDIO": TRANSCRIBE_QUEUE,
            "ANALYZE_CONTENT": ANALYZE_QUEUE,
            "RENDER_CLIPS": RENDER_QUEUE,
        }
        for queue in (task_queues[task] for task in self._settings.enabled_tasks):
            channel.basic_consume(queue, self._on_message, auto_ack=False)
        self.health.connected = True
        self.health.last_error = None
        logger.info("Media worker is consuming tasks %s", self._settings.enabled_tasks)
        channel.start_consuming()

    def _on_message(
        self,
        channel: pika.adapters.blocking_connection.BlockingChannel,
        method: pika.spec.Basic.Deliver,
        properties: pika.BasicProperties,
        body: bytes,
    ) -> None:
        started = time.monotonic()
        try:
            command = parse_command(body)
            if properties.message_id and UUID(properties.message_id) != command.message_id:
                raise ValueError("AMQP message id does not match payload")
        except (ValidationError, ValueError) as exc:
            logger.error("Invalid command rejected: %s", exc)
            self.metrics.observe_task("invalid", "rejected", time.monotonic() - started)
            channel.basic_reject(method.delivery_tag, requeue=False)
            return

        cached = self._inbox.result_for(command.message_id)
        if cached is not None:
            self._publish_result(channel, cached)
            channel.basic_ack(method.delivery_tag)
            self.metrics.observe_task(command.task_type, "cached", time.monotonic() - started)
            return

        try:
            details = self._execute(command)
            result = MediaTaskResultV1.success(command, details)
            self._complete(channel, method.delivery_tag, command.message_id, result)
            self.metrics.observe_task(command.task_type, "succeeded", time.monotonic() - started)
            logger.info("Media task completed", extra=log_context(command))
        except InvalidStorageKey as exc:
            result = MediaTaskResultV1.failure(command, "INVALID_STORAGE_KEY", str(exc))
            self._complete(channel, method.delivery_tag, command.message_id, result, reject=True)
            self.metrics.observe_task(command.task_type, "failed", time.monotonic() - started)
        except MediaTaskExecutionError as exc:
            if exc.retryable:
                outcome = self._retry_or_fail(channel, method.delivery_tag, properties, body, command, exc)
                self.metrics.observe_task(command.task_type, outcome, time.monotonic() - started)
            else:
                result = MediaTaskResultV1.failure(command, exc.code, str(exc))
                self._complete(channel, method.delivery_tag, command.message_id, result, reject=True)
                self.metrics.observe_task(command.task_type, "failed", time.monotonic() - started)
        except (MediaTemporarilyUnavailable, OSError) as exc:
            outcome = self._retry_or_fail(channel, method.delivery_tag, properties, body, command, exc)
            self.metrics.observe_task(command.task_type, outcome, time.monotonic() - started)
        except Exception as exc:
            logger.exception("Unexpected media task failure", extra=log_context(command))
            outcome = self._retry_or_fail(channel, method.delivery_tag, properties, body, command, exc)
            self.metrics.observe_task(command.task_type, outcome, time.monotonic() - started)

    def _execute(self, command: MediaCommand) -> dict[str, object]:
        if isinstance(command, MediaValidationCommandV1):
            return self._validator.validate(command.storage_key, command.video_id)
        if isinstance(command, ExtractAudioCommandV1):
            return self._audio_extractor.extract(command)
        if isinstance(command, TranscribeAudioCommandV1):
            return self._transcriber.transcribe(command)
        if isinstance(command, AnalyzeContentCommandV1):
            return self._analyzer.analyze(command)
        if isinstance(command, (RenderClipsCommandV1, RenderClipsCommandV2)):
            return self._renderer.render(command)
        raise MediaTaskExecutionError("UNSUPPORTED_TASK", "Unsupported media task", False)

    def _retry_or_fail(
        self,
        channel: pika.adapters.blocking_connection.BlockingChannel,
        delivery_tag: int,
        properties: pika.BasicProperties,
        body: bytes,
        command: MediaCommand,
        error: Exception,
    ) -> str:
        retries = retry_count(properties.headers)
        if retries >= self._settings.max_retries:
            code = error.code if isinstance(error, MediaTaskExecutionError) else "MEDIA_UNAVAILABLE"
            result = MediaTaskResultV1.failure(command, code, str(error))
            self._complete(channel, delivery_tag, command.message_id, result, reject=True)
            return "failed"
        headers = dict(properties.headers or {})
        headers[RETRY_HEADER] = retries + 1
        retry_properties = pika.BasicProperties(
            content_type="application/json",
            content_encoding="utf-8",
            delivery_mode=pika.DeliveryMode.Persistent,
            message_id=str(command.message_id),
            type=properties.type or f"{command.task_type}_REQUESTED_V1",
            headers=headers,
            timestamp=int(time.time()),
        )
        try:
            published = channel.basic_publish(
                exchange=RETRY_EXCHANGE,
                routing_key=retry_routing_key(command),
                body=body,
                properties=retry_properties,
                mandatory=True,
            )
            if published is False:
                raise RuntimeError("RabbitMQ did not confirm retry publish")
            channel.basic_ack(delivery_tag)
            logger.warning("Command scheduled for retry messageId=%s retry=%s", command.message_id, retries + 1)
            return "retry"
        except Exception:
            logger.exception("Retry publish failed; original command will be requeued")
            channel.basic_nack(delivery_tag, requeue=True)
            return "requeued"

    def _complete(
        self,
        channel: pika.adapters.blocking_connection.BlockingChannel,
        delivery_tag: int,
        command_message_id: UUID,
        result: MediaTaskResultV1,
        reject: bool = False,
    ) -> None:
        payload = result.json_bytes()
        self._publish_result(channel, payload, result.message_id)
        self._inbox.complete(command_message_id, payload)
        self.health.processed += 1
        if reject:
            channel.basic_reject(delivery_tag, requeue=False)
        else:
            channel.basic_ack(delivery_tag)

    def _publish_result(
        self,
        channel: pika.adapters.blocking_connection.BlockingChannel,
        payload: bytes,
        result_id: UUID | None = None,
    ) -> None:
        if result_id is None:
            result_id = MediaTaskResultV1.model_validate_json(payload).message_id
        published = channel.basic_publish(
            exchange=RESULT_EXCHANGE,
            routing_key="media.result",
            body=payload,
            properties=pika.BasicProperties(
                content_type="application/json",
                content_encoding="utf-8",
                delivery_mode=pika.DeliveryMode.Persistent,
                message_id=str(result_id),
                type="MEDIA_TASK_RESULT_V1",
                timestamp=int(time.time()),
            ),
            mandatory=True,
        )
        if published is False:
            raise RuntimeError("RabbitMQ did not confirm result publish")

    def _declare_topology(self, channel: pika.adapters.blocking_connection.BlockingChannel) -> None:
        for exchange in (COMMAND_EXCHANGE, RESULT_EXCHANGE, RETRY_EXCHANGE, DLX_EXCHANGE):
            channel.exchange_declare(exchange=exchange, exchange_type="direct", durable=True)

        self._declare_command_lane(channel, COMMAND_QUEUE, COMMAND_RETRY_QUEUE, COMMAND_DLQ, "media.validate")
        self._declare_command_lane(channel, EXTRACT_QUEUE, EXTRACT_RETRY_QUEUE, EXTRACT_DLQ,
                                   "media.extract-audio")
        self._declare_command_lane(channel, TRANSCRIBE_QUEUE, TRANSCRIBE_RETRY_QUEUE, TRANSCRIBE_DLQ,
                                   "media.transcribe")
        self._declare_command_lane(channel, ANALYZE_QUEUE, ANALYZE_RETRY_QUEUE, ANALYZE_DLQ,
                                   "media.analyze")
        self._declare_command_lane(channel, RENDER_QUEUE, RENDER_RETRY_QUEUE, RENDER_DLQ,
                                   "media.render")
        channel.queue_declare(
            queue=RESULT_QUEUE,
            durable=True,
            arguments={
                "x-queue-type": "quorum",
                "x-dead-letter-exchange": DLX_EXCHANGE,
                "x-dead-letter-routing-key": "media.result.dead",
                "x-dead-letter-strategy": "at-least-once",
                "x-overflow": "reject-publish",
            },
        )
        channel.queue_declare(
            queue=RESULT_RETRY_QUEUE,
            durable=True,
            arguments={
                "x-queue-type": "quorum",
                "x-message-ttl": self._settings.retry_delay_ms,
                "x-dead-letter-exchange": RESULT_EXCHANGE,
                "x-dead-letter-routing-key": "media.result",
                "x-dead-letter-strategy": "at-least-once",
                "x-overflow": "reject-publish",
            },
        )
        channel.queue_declare(queue=RESULT_DLQ, durable=True, arguments={"x-queue-type": "quorum"})
        channel.queue_bind(queue=RESULT_QUEUE, exchange=RESULT_EXCHANGE, routing_key="media.result")
        channel.queue_bind(queue=RESULT_RETRY_QUEUE, exchange=RETRY_EXCHANGE, routing_key="media.result.retry")
        channel.queue_bind(queue=RESULT_DLQ, exchange=DLX_EXCHANGE, routing_key="media.result.dead")

    def _declare_command_lane(self, channel: pika.adapters.blocking_connection.BlockingChannel,
                              queue: str, retry_queue: str, dead_queue: str, routing_key: str) -> None:
        channel.queue_declare(queue=queue, durable=True, arguments={
            "x-queue-type": "quorum",
            "x-dead-letter-exchange": DLX_EXCHANGE,
            "x-dead-letter-routing-key": f"{routing_key}.dead",
            "x-dead-letter-strategy": "at-least-once",
            "x-overflow": "reject-publish",
        })
        channel.queue_declare(queue=retry_queue, durable=True, arguments={
            "x-queue-type": "quorum",
            "x-message-ttl": self._settings.retry_delay_ms,
            "x-dead-letter-exchange": COMMAND_EXCHANGE,
            "x-dead-letter-routing-key": routing_key,
            "x-dead-letter-strategy": "at-least-once",
            "x-overflow": "reject-publish",
        })
        channel.queue_declare(queue=dead_queue, durable=True, arguments={"x-queue-type": "quorum"})
        channel.queue_bind(queue=queue, exchange=COMMAND_EXCHANGE, routing_key=routing_key)
        channel.queue_bind(queue=retry_queue, exchange=RETRY_EXCHANGE, routing_key=f"{routing_key}.retry")
        channel.queue_bind(queue=dead_queue, exchange=DLX_EXCHANGE, routing_key=f"{routing_key}.dead")


def retry_count(headers: dict[str, object] | None) -> int:
    raw = (headers or {}).get(RETRY_HEADER, 0)
    try:
        value = int(raw)  # type: ignore[arg-type]
    except (TypeError, ValueError):
        return 0
    return max(0, value)


def retry_routing_key(command: MediaCommand) -> str:
    return {
        "VALIDATE_MEDIA": "media.validate.retry",
        "EXTRACT_AUDIO": "media.extract-audio.retry",
        "TRANSCRIBE_AUDIO": "media.transcribe.retry",
        "ANALYZE_CONTENT": "media.analyze.retry",
        "RENDER_CLIPS": "media.render.retry",
    }[command.task_type]


def log_context(command: MediaCommand) -> dict[str, str]:
    return {
        "correlationId": command.correlation_id,
        "jobId": str(command.job_id),
        "videoId": str(command.video_id),
        "messageId": str(command.message_id),
        "taskType": command.task_type,
    }
