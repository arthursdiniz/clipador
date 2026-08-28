import json
from datetime import datetime, timezone
from pathlib import Path
from uuid import uuid4

from clipador_worker.artifacts import JobArtifactStorage
from clipador_worker.config import Settings
from clipador_worker.contracts import RenderClipsCommandV1, RenderFormatSpec
from clipador_worker.audio import MediaTaskExecutionError
from clipador_worker.rendering import (FfmpegClipRenderer, caption_cues, render_ass,
                                       render_command, render_srt, render_vtt)
from clipador_worker.storage import LocalMediaValidator


def test_generates_srt_vtt_and_styled_ass_from_word_timestamps() -> None:
    transcript = {"segments": [{"start": 10, "end": 12, "text": "Olá mundo",
                                "words": [{"start": 10.1, "end": 10.6, "word": "Olá"},
                                          {"start": 10.6, "end": 11.2, "word": "mundo"}]}]}
    cues = caption_cues(transcript, 10, 20)

    assert "00:00:00,100 --> 00:00:01,200" in render_srt(cues)
    assert render_vtt(cues).startswith("WEBVTT")
    assert "{\\kf50}Olá" in render_ass(cues, 1080, 1920)


def test_render_command_uses_fixed_codecs_and_argument_array(tmp_path: Path) -> None:
    command = render_contract(uuid4(), uuid4())
    arguments = render_command("ffmpeg", tmp_path / "input with spaces.mp4",
                               tmp_path / "output.mp4", tmp_path / "captions.ass",
                               command.candidates[0], command.formats[0], command)

    assert arguments[0] == "ffmpeg"
    assert arguments[arguments.index("-c:v") + 1] == "libx264"
    assert arguments[arguments.index("-c:a") + 1] == "aac"
    assert "subtitles=filename=" in arguments[arguments.index("-filter_complex") + 1]
    assert arguments[-2:] == ["-f", "mp4"] or arguments[-1].endswith("output.mp4")


def test_renderer_is_idempotent_and_writes_partial_manifest_contract(tmp_path: Path) -> None:
    job_id, video_id = uuid4(), uuid4()
    source = tmp_path / "videos" / str(video_id) / "original.mp4"
    source.parent.mkdir(parents=True)
    source.write_bytes(b"source-video")
    transcript = tmp_path / "jobs" / str(job_id) / "transcript" / "transcript.json"
    transcript.parent.mkdir(parents=True)
    transcript.write_text(json.dumps({
        "schemaVersion": 1, "jobId": str(job_id), "videoId": str(video_id),
        "segments": [{"start": 10, "end": 30, "text": "Uma ideia completa.",
                      "words": [{"start": 10.1, "end": 10.8, "word": "Uma"},
                                {"start": 10.8, "end": 11.5, "word": "ideia"},
                                {"start": 11.5, "end": 12.3, "word": "completa."}]}],
    }), encoding="utf-8")
    settings = Settings("rabbit", 5672, "user", "password", tmp_path,
                        tmp_path / "inbox.sqlite3", 5, 30000, 30)
    renderer = FfmpegClipRenderer(settings, JobArtifactStorage(tmp_path), LocalMediaValidator(tmp_path))

    command = render_contract(job_id, video_id)
    command = command.model_copy(update={"formats": [*command.formats, RenderFormatSpec(
        format="SQUARE_1_1", width=1080, height=1080)]})

    def fake_run(arguments: list[str], _code: str) -> None:
        target = Path(arguments[-1])
        if "square_1_1" in target.as_posix() and "mp4" in arguments:
            raise MediaTaskExecutionError("FORMAT_RENDER_FAILED", "unsupported test format", False)
        if "mp4" in arguments:
            target.write_bytes(b"\x00\x00\x00\x18ftypisom" + b"x" * 32)
        else:
            target.write_bytes(b"\xff\xd8\xff\xe0jpeg")

    renderer._run = fake_run

    first = renderer.render(command)
    second = renderer.render(command)

    manifest = json.loads((tmp_path / command.manifest_storage_key).read_text(encoding="utf-8"))
    assert first["succeededCount"] == 1
    assert first["failedCount"] == 1
    assert second["reused"] is True
    successful = next(item for item in manifest["renders"] if item["status"] == "SUCCEEDED")
    failed = next(item for item in manifest["renders"] if item["status"] == "FAILED")
    assert successful["storageKey"].endswith("clip.mp4")
    assert (tmp_path / successful["srtStorageKey"]).is_file()
    assert failed["errorCode"] == "FORMAT_RENDER_FAILED"


def render_contract(job_id, video_id) -> RenderClipsCommandV1:
    candidate_id = uuid4()
    return RenderClipsCommandV1.model_validate({
        "schemaVersion": 1, "messageId": str(uuid4()), "taskType": "RENDER_CLIPS",
        "jobId": str(job_id), "videoId": str(video_id), "correlationId": "correlation",
        "videoStorageKey": f"videos/{video_id}/original.mp4",
        "transcriptStorageKey": f"jobs/{job_id}/transcript/transcript.json",
        "manifestStorageKey": f"jobs/{job_id}/render/manifest.json",
        "candidates": [{"candidateId": str(candidate_id), "start": 10, "end": 30}],
        "formats": [{"format": "VERTICAL_9_16", "width": 1080, "height": 1920}],
        "burnInSubtitles": True, "videoCrf": 21, "encoderPreset": "medium",
        "audioBitrateKbps": 160, "outputFps": 30, "attempt": 1,
        "createdAt": datetime.now(timezone.utc).isoformat(),
    })
