from __future__ import annotations

import subprocess
from pathlib import Path

from .artifacts import JobArtifactStorage
from .config import Settings
from .contracts import ExtractAudioCommandV1
from .storage import LocalMediaValidator


class MediaTaskExecutionError(RuntimeError):
    def __init__(self, code: str, message: str, retryable: bool) -> None:
        super().__init__(message)
        self.code = code
        self.retryable = retryable


class FfmpegAudioExtractor:
    def __init__(self, settings: Settings, validator: LocalMediaValidator,
                 artifacts: JobArtifactStorage) -> None:
        self._settings = settings
        self._validator = validator
        self._artifacts = artifacts

    def extract(self, command: ExtractAudioCommandV1) -> dict[str, object]:
        source = self._validator.resolve_video(command.input_storage_key, command.video_id)
        target = self._artifacts.output_target(command.output_storage_key, command.job_id)
        if self._valid_wave(target):
            return self._details(command, target, reused=True)

        temporary = self._artifacts.temporary(target, command.message_id)
        temporary.unlink(missing_ok=True)
        arguments = self.command(source, temporary, command.sample_rate, command.channels)
        try:
            completed = subprocess.run(
                arguments,
                stdin=subprocess.DEVNULL,
                stdout=subprocess.DEVNULL,
                stderr=subprocess.PIPE,
                text=True,
                timeout=self._settings.ffmpeg_timeout_seconds,
                check=False,
            )
            if completed.returncode != 0:
                error = (completed.stderr or "FFmpeg audio extraction failed")[-4000:]
                raise MediaTaskExecutionError("AUDIO_EXTRACTION_FAILED", error, retryable=False)
            if not self._valid_wave(temporary):
                raise MediaTaskExecutionError("INVALID_NORMALIZED_AUDIO", "FFmpeg produced an invalid WAV", False)
            if temporary.stat().st_size > self._settings.max_audio_bytes:
                raise MediaTaskExecutionError("NORMALIZED_AUDIO_TOO_LARGE", "Normalized audio exceeds limit", False)
            self._artifacts.commit(temporary, target)
            return self._details(command, target, reused=False)
        except subprocess.TimeoutExpired as exc:
            raise MediaTaskExecutionError("AUDIO_EXTRACTION_TIMEOUT", "FFmpeg extraction timed out", True) from exc
        except OSError as exc:
            raise MediaTaskExecutionError("FFMPEG_UNAVAILABLE", str(exc), True) from exc
        finally:
            temporary.unlink(missing_ok=True)

    def command(self, source: Path, target: Path, sample_rate: int, channels: int) -> list[str]:
        return [
            self._settings.ffmpeg_executable, "-nostdin", "-hide_banner", "-loglevel", "error",
            "-y", "-i", str(source), "-map", "0:a:0", "-vn", "-sn", "-dn",
            "-ac", str(channels), "-ar", str(sample_rate), "-c:a", "pcm_s16le", "-f", "wav", str(target),
        ]

    def _valid_wave(self, path: Path) -> bool:
        if not path.is_file() or path.stat().st_size <= 44:
            return False
        with path.open("rb") as stream:
            header = stream.read(12)
        return header[:4] == b"RIFF" and header[8:12] == b"WAVE"

    def _details(self, command: ExtractAudioCommandV1, target: Path, reused: bool) -> dict[str, object]:
        return {
            "audioStorageKey": command.output_storage_key,
            "sizeBytes": target.stat().st_size,
            "sampleRate": command.sample_rate,
            "channels": command.channels,
            "codec": "pcm_s16le",
            "reused": reused,
        }
