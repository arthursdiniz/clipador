from __future__ import annotations

from datetime import datetime, timezone
from enum import StrEnum
import json
from typing import Any, Literal, TypeAlias
from uuid import UUID, uuid4

from pydantic import BaseModel, ConfigDict, Field, field_validator


class MediaValidationCommandV1(BaseModel):
    model_config = ConfigDict(extra="forbid", frozen=True)

    schema_version: Literal[1] = Field(alias="schemaVersion")
    message_id: UUID = Field(alias="messageId")
    task_type: Literal["VALIDATE_MEDIA"] = Field(alias="taskType")
    job_id: UUID = Field(alias="jobId")
    video_id: UUID = Field(alias="videoId")
    correlation_id: str = Field(alias="correlationId", min_length=1, max_length=100)
    storage_key: str = Field(alias="storageKey", min_length=1, max_length=1024)
    attempt: int = Field(ge=1)
    created_at: datetime = Field(alias="createdAt")

    @field_validator("created_at")
    @classmethod
    def timestamp_must_be_aware(cls, value: datetime) -> datetime:
        if value.tzinfo is None:
            raise ValueError("createdAt must include a timezone")
        return value


class ExtractAudioCommandV1(BaseModel):
    model_config = ConfigDict(extra="forbid", frozen=True)

    schema_version: Literal[1] = Field(alias="schemaVersion")
    message_id: UUID = Field(alias="messageId")
    task_type: Literal["EXTRACT_AUDIO"] = Field(alias="taskType")
    job_id: UUID = Field(alias="jobId")
    video_id: UUID = Field(alias="videoId")
    correlation_id: str = Field(alias="correlationId", min_length=1, max_length=100)
    input_storage_key: str = Field(alias="inputStorageKey", min_length=1, max_length=1024)
    output_storage_key: str = Field(alias="outputStorageKey", min_length=1, max_length=1024)
    sample_rate: Literal[16000] = Field(alias="sampleRate")
    channels: Literal[1]
    attempt: int = Field(ge=1)
    created_at: datetime = Field(alias="createdAt")

    @field_validator("created_at")
    @classmethod
    def timestamp_must_be_aware(cls, value: datetime) -> datetime:
        if value.tzinfo is None:
            raise ValueError("createdAt must include a timezone")
        return value


class TranscribeAudioCommandV1(BaseModel):
    model_config = ConfigDict(extra="forbid", frozen=True)

    schema_version: Literal[1] = Field(alias="schemaVersion")
    message_id: UUID = Field(alias="messageId")
    task_type: Literal["TRANSCRIBE_AUDIO"] = Field(alias="taskType")
    job_id: UUID = Field(alias="jobId")
    video_id: UUID = Field(alias="videoId")
    correlation_id: str = Field(alias="correlationId", min_length=1, max_length=100)
    audio_storage_key: str = Field(alias="audioStorageKey", min_length=1, max_length=1024)
    transcript_storage_key: str = Field(alias="transcriptStorageKey", min_length=1, max_length=1024)
    language: str | None = Field(default=None, min_length=2, max_length=20)
    word_timestamps: Literal[True] = Field(alias="wordTimestamps")
    vad_enabled: Literal[True] = Field(alias="vadEnabled")
    attempt: int = Field(ge=1)
    created_at: datetime = Field(alias="createdAt")

    @field_validator("created_at")
    @classmethod
    def timestamp_must_be_aware(cls, value: datetime) -> datetime:
        if value.tzinfo is None:
            raise ValueError("createdAt must include a timezone")
        return value


class AnalyzeContentCommandV1(BaseModel):
    model_config = ConfigDict(extra="forbid", frozen=True)

    schema_version: Literal[1] = Field(alias="schemaVersion")
    message_id: UUID = Field(alias="messageId")
    task_type: Literal["ANALYZE_CONTENT"] = Field(alias="taskType")
    job_id: UUID = Field(alias="jobId")
    video_id: UUID = Field(alias="videoId")
    correlation_id: str = Field(alias="correlationId", min_length=1, max_length=100)
    video_storage_key: str = Field(alias="videoStorageKey", min_length=1, max_length=1024)
    audio_storage_key: str = Field(alias="audioStorageKey", min_length=1, max_length=1024)
    transcript_storage_key: str = Field(alias="transcriptStorageKey", min_length=1, max_length=1024)
    analysis_storage_key: str = Field(alias="analysisStorageKey", min_length=1, max_length=1024)
    min_duration_seconds: float = Field(alias="minDurationSeconds", ge=5, le=180)
    ideal_duration_seconds: float = Field(alias="idealDurationSeconds", ge=5, le=180)
    max_duration_seconds: float = Field(alias="maxDurationSeconds", ge=5, le=180)
    max_candidates: int = Field(alias="maxCandidates", ge=1, le=1000)
    semantic_weight: float = Field(alias="semanticWeight", ge=0, le=1)
    audio_weight: float = Field(alias="audioWeight", ge=0, le=1)
    visual_weight: float = Field(alias="visualWeight", ge=0, le=1)
    narrative_weight: float = Field(alias="narrativeWeight", ge=0, le=1)
    hook_weight: float = Field(alias="hookWeight", ge=0, le=1)
    context_penalty_weight: float = Field(alias="contextPenaltyWeight", ge=0, le=1)
    attempt: int = Field(ge=1)
    created_at: datetime = Field(alias="createdAt")

    @field_validator("created_at")
    @classmethod
    def analysis_timestamp_must_be_aware(cls, value: datetime) -> datetime:
        if value.tzinfo is None:
            raise ValueError("createdAt must include a timezone")
        return value

    @field_validator("max_duration_seconds")
    @classmethod
    def durations_must_be_ordered(cls, value: float, info: Any) -> float:
        minimum = info.data.get("min_duration_seconds")
        ideal = info.data.get("ideal_duration_seconds")
        if minimum is not None and ideal is not None and not minimum <= ideal <= value:
            raise ValueError("Clip durations must be ordered")
        return value


class RenderCandidateSpec(BaseModel):
    model_config = ConfigDict(extra="forbid", frozen=True)
    candidate_id: UUID = Field(alias="candidateId")
    start: float = Field(ge=0)
    end: float = Field(gt=0, le=100_000)

    @field_validator("end")
    @classmethod
    def valid_window(cls, value: float, info: Any) -> float:
        start = info.data.get("start")
        if start is not None and (value <= start or value - start > 180):
            raise ValueError("Render candidate window is invalid")
        return value


class RenderFormatSpec(BaseModel):
    model_config = ConfigDict(extra="forbid", frozen=True)
    format: Literal["VERTICAL_9_16", "LANDSCAPE_16_9", "SQUARE_1_1"]
    width: int = Field(ge=240, le=3840, multiple_of=2)
    height: int = Field(ge=240, le=3840, multiple_of=2)


class RenderClipsCommandV1(BaseModel):
    model_config = ConfigDict(extra="forbid", frozen=True)

    schema_version: Literal[1] = Field(alias="schemaVersion")
    message_id: UUID = Field(alias="messageId")
    task_type: Literal["RENDER_CLIPS"] = Field(alias="taskType")
    job_id: UUID = Field(alias="jobId")
    video_id: UUID = Field(alias="videoId")
    correlation_id: str = Field(alias="correlationId", min_length=1, max_length=100)
    video_storage_key: str = Field(alias="videoStorageKey", min_length=1, max_length=1024)
    transcript_storage_key: str = Field(alias="transcriptStorageKey", min_length=1, max_length=1024)
    manifest_storage_key: str = Field(alias="manifestStorageKey", min_length=1, max_length=1024)
    candidates: list[RenderCandidateSpec] = Field(min_length=1, max_length=100)
    formats: list[RenderFormatSpec] = Field(min_length=1, max_length=3)
    burn_in_subtitles: bool = Field(alias="burnInSubtitles")
    video_crf: int = Field(alias="videoCrf", ge=16, le=32)
    encoder_preset: Literal["ultrafast", "superfast", "veryfast", "faster", "fast",
                            "medium", "slow", "slower"] = Field(alias="encoderPreset")
    audio_bitrate_kbps: int = Field(alias="audioBitrateKbps", ge=64, le=320)
    output_fps: int = Field(alias="outputFps", ge=24, le=60)
    attempt: int = Field(ge=1)
    created_at: datetime = Field(alias="createdAt")

    @field_validator("created_at")
    @classmethod
    def render_timestamp_must_be_aware(cls, value: datetime) -> datetime:
        if value.tzinfo is None:
            raise ValueError("createdAt must include a timezone")
        return value


class RenderClipsCommandV2(RenderClipsCommandV1):
    schema_version: Literal[2] = Field(alias="schemaVersion")
    smart_reframing_enabled: bool = Field(alias="smartReframingEnabled")
    reframing_mode: Literal["AUTO", "FOCUS", "GROUP", "BLURRED_BACKGROUND"] = Field(
        alias="reframingMode")
    reframing_sample_fps: float = Field(alias="reframingSampleFps", ge=.25, le=5)
    reframing_smoothing: float = Field(alias="reframingSmoothing", ge=0, le=.98)
    reframing_max_pan_ratio_per_second: float = Field(
        alias="reframingMaxPanRatioPerSecond", ge=.05, le=1)
    reframing_face_min_size_ratio: float = Field(
        alias="reframingFaceMinSizeRatio", ge=.005, le=.25)
    reframing_detection_width: int = Field(alias="reframingDetectionWidth", ge=160, le=1280)
    reframing_max_keyframes: int = Field(alias="reframingMaxKeyframes", ge=2, le=256)


MediaCommand: TypeAlias = (MediaValidationCommandV1 | ExtractAudioCommandV1
                           | TranscribeAudioCommandV1 | AnalyzeContentCommandV1
                           | RenderClipsCommandV1 | RenderClipsCommandV2)


def parse_command(body: bytes) -> MediaCommand:
    envelope = json.loads(body)
    task_type = envelope.get("taskType") if isinstance(envelope, dict) else None
    contract = {
        "VALIDATE_MEDIA": MediaValidationCommandV1,
        "EXTRACT_AUDIO": ExtractAudioCommandV1,
        "TRANSCRIBE_AUDIO": TranscribeAudioCommandV1,
        "ANALYZE_CONTENT": AnalyzeContentCommandV1,
        "RENDER_CLIPS": (RenderClipsCommandV2 if envelope.get("schemaVersion") == 2
                         else RenderClipsCommandV1),
    }.get(task_type)
    if contract is None:
        raise ValueError(f"Unsupported task type: {task_type}")
    return contract.model_validate(envelope)


class ResultStatus(StrEnum):
    SUCCEEDED = "SUCCEEDED"
    FAILED = "FAILED"


class MediaTaskResultV1(BaseModel):
    model_config = ConfigDict(extra="forbid", frozen=True)

    schema_version: Literal[1] = Field(default=1, alias="schemaVersion")
    message_id: UUID = Field(default_factory=uuid4, alias="messageId")
    command_message_id: UUID = Field(alias="commandMessageId")
    task_type: Literal["VALIDATE_MEDIA", "EXTRACT_AUDIO", "TRANSCRIBE_AUDIO", "ANALYZE_CONTENT", "RENDER_CLIPS"] = Field(alias="taskType")
    job_id: UUID = Field(alias="jobId")
    video_id: UUID = Field(alias="videoId")
    correlation_id: str = Field(alias="correlationId")
    status: ResultStatus
    error_code: str | None = Field(default=None, alias="errorCode")
    error_message: str | None = Field(default=None, alias="errorMessage")
    details: dict[str, Any] = Field(default_factory=dict)
    completed_at: datetime = Field(default_factory=lambda: datetime.now(timezone.utc), alias="completedAt")

    @classmethod
    def success(cls, command: MediaCommand, details: dict[str, Any]) -> "MediaTaskResultV1":
        return cls(
            messageId=command.message_id,
            commandMessageId=command.message_id,
            taskType=command.task_type,
            jobId=command.job_id,
            videoId=command.video_id,
            correlationId=command.correlation_id,
            status=ResultStatus.SUCCEEDED,
            details=details,
        )

    @classmethod
    def failure(cls, command: MediaCommand, code: str, message: str) -> "MediaTaskResultV1":
        return cls(
            messageId=command.message_id,
            commandMessageId=command.message_id,
            taskType=command.task_type,
            jobId=command.job_id,
            videoId=command.video_id,
            correlationId=command.correlation_id,
            status=ResultStatus.FAILED,
            errorCode=code[:100],
            errorMessage=message[:4000],
        )

    def json_bytes(self) -> bytes:
        return self.model_dump_json(by_alias=True).encode("utf-8")
