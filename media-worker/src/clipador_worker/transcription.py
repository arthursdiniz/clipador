from __future__ import annotations

import json
import math
import threading
from pathlib import Path
from typing import Any, Protocol

from .artifacts import JobArtifactStorage
from .audio import MediaTaskExecutionError
from .config import Settings
from .contracts import TranscribeAudioCommandV1


class TranscriptionProvider(Protocol):
    def transcribe(self, command: TranscribeAudioCommandV1) -> dict[str, object]: ...


class FasterWhisperTranscriber:
    def __init__(self, settings: Settings, artifacts: JobArtifactStorage) -> None:
        self._settings = settings
        self._artifacts = artifacts
        self._model: Any | None = None
        self._model_lock = threading.Lock()

    def transcribe(self, command: TranscribeAudioCommandV1) -> dict[str, object]:
        audio = self._artifacts.resolve_input(command.audio_storage_key, command.job_id)
        target = self._artifacts.output_target(command.transcript_storage_key, command.job_id)
        existing = self._existing_details(target, command)
        if existing is not None:
            return existing

        model = self._model_instance()
        language = command.language or self._settings.default_language
        try:
            generated, info = model.transcribe(
                str(audio),
                language=language,
                beam_size=self._settings.whisper_beam_size,
                vad_filter=command.vad_enabled,
                vad_parameters={"min_silence_duration_ms": self._settings.whisper_vad_min_silence_ms},
                word_timestamps=command.word_timestamps,
                condition_on_previous_text=True,
            )
            segments = [self._segment(index, segment) for index, segment in enumerate(generated)]
        except MediaTaskExecutionError:
            raise
        except Exception as exc:
            raise MediaTaskExecutionError("TRANSCRIPTION_ENGINE_FAILED", str(exc), True) from exc
        if not segments:
            raise MediaTaskExecutionError("NO_SPEECH_DETECTED", "No speech was detected in the audio", False)

        document = {
            "schemaVersion": 1,
            "jobId": str(command.job_id),
            "videoId": str(command.video_id),
            "engine": "faster-whisper",
            "modelName": self._settings.whisper_model,
            "detectedLanguage": info.language,
            "languageProbability": finite_number(info.language_probability),
            "wordTimestamps": command.word_timestamps,
            "durationSeconds": finite_number(info.duration),
            "durationAfterVad": finite_number(info.duration_after_vad),
            "fullText": " ".join(segment["text"] for segment in segments),
            "segments": segments,
        }
        temporary = self._artifacts.temporary(target, command.message_id)
        temporary.unlink(missing_ok=True)
        try:
            with temporary.open("x", encoding="utf-8") as stream:
                json.dump(document, stream, ensure_ascii=False, separators=(",", ":"), allow_nan=False)
            if temporary.stat().st_size > self._settings.max_transcript_bytes:
                raise MediaTaskExecutionError("TRANSCRIPT_ARTIFACT_TOO_LARGE", "Transcript exceeds limit", False)
            self._artifacts.commit(temporary, target)
        finally:
            temporary.unlink(missing_ok=True)
        return self._details(command, target, len(segments), reused=False, document=document)

    def _model_instance(self) -> Any:
        if self._model is not None:
            return self._model
        with self._model_lock:
            if self._model is None:
                try:
                    from faster_whisper import WhisperModel

                    self._settings.whisper_model_cache.mkdir(parents=True, exist_ok=True)
                    self._model = WhisperModel(
                        self._settings.whisper_model,
                        device=self._settings.whisper_device,
                        compute_type=self._settings.whisper_compute_type,
                        download_root=str(self._settings.whisper_model_cache),
                    )
                except Exception as exc:
                    raise MediaTaskExecutionError("WHISPER_MODEL_UNAVAILABLE", str(exc), True) from exc
        return self._model

    def _segment(self, index: int, segment: Any) -> dict[str, object]:
        start = finite_number(segment.start)
        end = finite_number(segment.end)
        text = " ".join(str(segment.text).split())
        if start is None or end is None or start < 0 or end <= start or not text:
            raise MediaTaskExecutionError("INVALID_TRANSCRIPTION_SEGMENT", "Whisper returned invalid segment", False)
        words = []
        for word in segment.words or []:
            word_start = finite_number(word.start)
            word_end = finite_number(word.end)
            token = str(word.word).strip()
            if word_start is None or word_end is None or word_end <= word_start or not token:
                continue
            words.append({
                "start": word_start,
                "end": word_end,
                "word": token,
                "probability": finite_number(word.probability),
            })
        average_log_probability = finite_number(segment.avg_logprob)
        confidence = None if average_log_probability is None else min(1.0, math.exp(min(0.0, average_log_probability)))
        return {"index": index, "start": start, "end": end, "text": text,
                "confidence": confidence, "words": words}

    def _existing_details(self, target: Path, command: TranscribeAudioCommandV1) -> dict[str, object] | None:
        if not target.is_file() or target.stat().st_size <= 0:
            return None
        try:
            with target.open("r", encoding="utf-8") as stream:
                document = json.load(stream)
            if document.get("schemaVersion") != 1 or document.get("jobId") != str(command.job_id):
                return None
            return self._details(command, target, len(document.get("segments", [])), reused=True, document=document)
        except (OSError, ValueError, TypeError):
            return None

    def _details(self, command: TranscribeAudioCommandV1, target: Path, segment_count: int,
                 reused: bool, document: dict[str, object]) -> dict[str, object]:
        return {
            "transcriptStorageKey": command.transcript_storage_key,
            "sizeBytes": target.stat().st_size,
            "segmentCount": segment_count,
            "detectedLanguage": document.get("detectedLanguage"),
            "modelName": document.get("modelName"),
            "reused": reused,
        }


def finite_number(value: object) -> float | None:
    if value is None:
        return None
    number = float(value)
    return number if math.isfinite(number) else None
