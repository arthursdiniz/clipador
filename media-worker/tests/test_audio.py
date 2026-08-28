from datetime import datetime, timezone
from pathlib import Path
from uuid import uuid4

from clipador_worker.artifacts import JobArtifactStorage
from clipador_worker.audio import FfmpegAudioExtractor
from clipador_worker.config import Settings
from clipador_worker.contracts import ExtractAudioCommandV1
from clipador_worker.storage import LocalMediaValidator


def settings(root: Path) -> Settings:
    return Settings("rabbit", 5672, "user", "password", root, root / "inbox.sqlite3", 5, 30000, 30)


def test_ffmpeg_command_is_argument_array_with_fixed_normalization(tmp_path: Path) -> None:
    extractor = FfmpegAudioExtractor(settings(tmp_path), LocalMediaValidator(tmp_path),
                                     JobArtifactStorage(tmp_path))
    command = extractor.command(Path("input with spaces.mp4"), Path("output.wav"), 16000, 1)

    assert command[0] == "ffmpeg"
    assert command[-3:] == ["-f", "wav", "output.wav"]
    assert command[command.index("-ar") + 1] == "16000"
    assert command[command.index("-ac") + 1] == "1"


def test_reuses_complete_deterministic_wave_artifact(tmp_path: Path) -> None:
    video_id, job_id = uuid4(), uuid4()
    source = tmp_path / "videos" / str(video_id) / "original.mp4"
    source.parent.mkdir(parents=True)
    source.write_bytes(b"video")
    target = tmp_path / "jobs" / str(job_id) / "audio" / "normalized.wav"
    target.parent.mkdir(parents=True)
    target.write_bytes(b"RIFF" + b"\x00" * 4 + b"WAVE" + b"\x00" * 40)
    command = ExtractAudioCommandV1(
        schemaVersion=1, messageId=uuid4(), taskType="EXTRACT_AUDIO", jobId=job_id,
        videoId=video_id, correlationId="correlation",
        inputStorageKey=f"videos/{video_id}/original.mp4",
        outputStorageKey=f"jobs/{job_id}/audio/normalized.wav", sampleRate=16000,
        channels=1, attempt=1, createdAt=datetime.now(timezone.utc),
    )

    result = FfmpegAudioExtractor(settings(tmp_path), LocalMediaValidator(tmp_path),
                                  JobArtifactStorage(tmp_path)).extract(command)

    assert result["reused"] is True
    assert result["audioStorageKey"] == command.output_storage_key
