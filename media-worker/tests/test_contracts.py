from datetime import datetime, timezone
from uuid import uuid4

import pytest
from pydantic import ValidationError

from clipador_worker.contracts import (ExtractAudioCommandV1, MediaTaskResultV1,
                                       AnalyzeContentCommandV1,
                                       RenderClipsCommandV1,
                                       RenderClipsCommandV2,
                                       MediaValidationCommandV1, ResultStatus,
                                       TranscribeAudioCommandV1, parse_command)


def command_payload() -> dict[str, object]:
    video_id = uuid4()
    return {
        "schemaVersion": 1,
        "messageId": str(uuid4()),
        "taskType": "VALIDATE_MEDIA",
        "jobId": str(uuid4()),
        "videoId": str(video_id),
        "correlationId": "correlation-1",
        "storageKey": f"videos/{video_id}/original.mp4",
        "attempt": 1,
        "createdAt": datetime.now(timezone.utc).isoformat(),
    }


def test_command_contract_and_result_use_camel_case() -> None:
    command = MediaValidationCommandV1.model_validate(command_payload())
    result = MediaTaskResultV1.success(command, {"sizeBytes": 42})

    serialized = result.model_dump(by_alias=True, mode="json")

    assert serialized["schemaVersion"] == 1
    assert serialized["messageId"] == str(command.message_id)
    assert serialized["commandMessageId"] == str(command.message_id)
    assert serialized["status"] == ResultStatus.SUCCEEDED


def test_unknown_contract_fields_are_rejected() -> None:
    payload = command_payload()
    payload["shellCommand"] = "unsafe"

    with pytest.raises(ValidationError):
        MediaValidationCommandV1.model_validate(payload)


def test_parses_stage_specific_contracts() -> None:
    common = {
        "schemaVersion": 1, "messageId": str(uuid4()), "jobId": str(uuid4()),
        "videoId": str(uuid4()), "correlationId": "correlation", "attempt": 1,
        "createdAt": datetime.now(timezone.utc).isoformat(),
    }
    extraction = parse_command(__import__("json").dumps({
        **common, "taskType": "EXTRACT_AUDIO", "inputStorageKey": "videos/id/original.mp4",
        "outputStorageKey": "jobs/id/audio/normalized.wav", "sampleRate": 16000, "channels": 1,
    }).encode())
    transcription = parse_command(__import__("json").dumps({
        **common, "messageId": str(uuid4()), "taskType": "TRANSCRIBE_AUDIO",
        "audioStorageKey": "jobs/id/audio/normalized.wav",
        "transcriptStorageKey": "jobs/id/transcript/transcript.json", "language": None,
        "wordTimestamps": True, "vadEnabled": True,
    }).encode())

    assert isinstance(extraction, ExtractAudioCommandV1)
    assert isinstance(transcription, TranscribeAudioCommandV1)


def test_parses_strict_analysis_contract() -> None:
    job_id, video_id = uuid4(), uuid4()
    parsed = parse_command(__import__("json").dumps({
        "schemaVersion": 1, "messageId": str(uuid4()), "taskType": "ANALYZE_CONTENT",
        "jobId": str(job_id), "videoId": str(video_id), "correlationId": "correlation",
        "videoStorageKey": f"videos/{video_id}/original.mp4",
        "audioStorageKey": f"jobs/{job_id}/audio/normalized.wav",
        "transcriptStorageKey": f"jobs/{job_id}/transcript/transcript.json",
        "analysisStorageKey": f"jobs/{job_id}/analysis/candidates.json",
        "minDurationSeconds": 20, "idealDurationSeconds": 45, "maxDurationSeconds": 90,
        "maxCandidates": 100, "semanticWeight": .30, "audioWeight": .12,
        "visualWeight": .08, "narrativeWeight": .22, "hookWeight": .23,
        "contextPenaltyWeight": .15, "attempt": 1,
        "createdAt": datetime.now(timezone.utc).isoformat(),
    }).encode())

    assert isinstance(parsed, AnalyzeContentCommandV1)


def test_parses_strict_render_contract() -> None:
    job_id, video_id, candidate_id = uuid4(), uuid4(), uuid4()
    parsed = parse_command(__import__("json").dumps({
        "schemaVersion": 1, "messageId": str(uuid4()), "taskType": "RENDER_CLIPS",
        "jobId": str(job_id), "videoId": str(video_id), "correlationId": "correlation",
        "videoStorageKey": f"videos/{video_id}/original.mp4",
        "transcriptStorageKey": f"jobs/{job_id}/transcript/transcript.json",
        "manifestStorageKey": f"jobs/{job_id}/render/manifest.json",
        "candidates": [{"candidateId": str(candidate_id), "start": 20, "end": 60}],
        "formats": [{"format": "VERTICAL_9_16", "width": 1080, "height": 1920}],
        "burnInSubtitles": True, "videoCrf": 21, "encoderPreset": "medium",
        "audioBitrateKbps": 160, "outputFps": 30, "attempt": 1,
        "createdAt": datetime.now(timezone.utc).isoformat(),
    }).encode())

    assert isinstance(parsed, RenderClipsCommandV1)


def test_parses_smart_render_contract_v2() -> None:
    job_id, video_id, candidate_id = uuid4(), uuid4(), uuid4()
    payload = {
        "schemaVersion": 2, "messageId": str(uuid4()), "taskType": "RENDER_CLIPS",
        "jobId": str(job_id), "videoId": str(video_id), "correlationId": "correlation",
        "videoStorageKey": f"videos/{video_id}/original.mp4",
        "transcriptStorageKey": f"jobs/{job_id}/transcript/transcript.json",
        "manifestStorageKey": f"jobs/{job_id}/render/manifest.json",
        "candidates": [{"candidateId": str(candidate_id), "start": 20, "end": 60}],
        "formats": [{"format": "VERTICAL_9_16", "width": 1080, "height": 1920}],
        "burnInSubtitles": True, "videoCrf": 21, "encoderPreset": "medium",
        "audioBitrateKbps": 160, "outputFps": 30,
        "smartReframingEnabled": True, "reframingMode": "AUTO",
        "reframingSampleFps": 1.5, "reframingSmoothing": .82,
        "reframingMaxPanRatioPerSecond": .35, "reframingFaceMinSizeRatio": .025,
        "reframingDetectionWidth": 640, "reframingMaxKeyframes": 64,
        "attempt": 1, "createdAt": datetime.now(timezone.utc).isoformat(),
    }

    parsed = parse_command(__import__("json").dumps(payload).encode())

    assert isinstance(parsed, RenderClipsCommandV2)
    assert parsed.reframing_mode == "AUTO"
