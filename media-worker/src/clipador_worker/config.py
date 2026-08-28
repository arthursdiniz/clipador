from __future__ import annotations

import os
from dataclasses import dataclass
from pathlib import Path
from urllib.parse import urlparse


@dataclass(frozen=True)
class Settings:
    rabbit_host: str
    rabbit_port: int
    rabbit_user: str
    rabbit_password: str
    storage_root: Path
    inbox_path: Path
    max_retries: int
    retry_delay_ms: int
    reconnect_max_seconds: int
    ffmpeg_executable: str = "ffmpeg"
    ffmpeg_timeout_seconds: int = 7200
    max_audio_bytes: int = 1_073_741_824
    max_transcript_bytes: int = 67_108_864
    whisper_model: str = "small"
    whisper_device: str = "cpu"
    whisper_compute_type: str = "int8"
    whisper_beam_size: int = 5
    whisper_vad_min_silence_ms: int = 500
    whisper_model_cache: Path = Path("./data/worker/models")
    default_language: str | None = None
    max_analysis_bytes: int = 16_777_216
    analysis_visual_fps: float = 1.0
    analysis_visual_width: int = 64
    analysis_visual_height: int = 36
    analysis_provider: str = "local"
    ollama_url: str = "http://127.0.0.1:11434/api/chat"
    ollama_model: str = "qwen3:4b"
    ollama_timeout_seconds: int = 180
    ollama_max_candidates: int = 20
    max_render_manifest_bytes: int = 16_777_216
    max_render_output_bytes: int = 2_147_483_648
    smart_reframing_enabled: bool = True
    reframing_mode: str = "AUTO"
    reframing_sample_fps: float = 1.5
    reframing_smoothing: float = 0.82
    reframing_max_pan_ratio_per_second: float = 0.35
    reframing_face_min_size_ratio: float = 0.025
    reframing_detection_width: int = 640
    reframing_max_keyframes: int = 64
    enabled_tasks: tuple[str, ...] = ("VALIDATE_MEDIA", "EXTRACT_AUDIO", "TRANSCRIBE_AUDIO", "ANALYZE_CONTENT", "RENDER_CLIPS")

    @classmethod
    def from_env(cls) -> "Settings":
        settings = cls(
            rabbit_host=os.getenv("RABBITMQ_HOST", "localhost"),
            rabbit_port=int(os.getenv("RABBITMQ_PORT", "5672")),
            rabbit_user=required("RABBITMQ_USER"),
            rabbit_password=required("RABBITMQ_PASSWORD"),
            storage_root=Path(os.getenv("CLIPADOR_STORAGE_ROOT", "./data/storage")),
            inbox_path=Path(os.getenv("CLIPADOR_WORKER_INBOX", "./data/worker/inbox.sqlite3")),
            max_retries=int(os.getenv("CLIPADOR_WORKER_MAX_RETRIES", "5")),
            retry_delay_ms=int(os.getenv("CLIPADOR_RABBIT_RETRY_DELAY_MS", "30000")),
            reconnect_max_seconds=int(os.getenv("CLIPADOR_WORKER_RECONNECT_MAX_SECONDS", "30")),
            ffmpeg_executable=os.getenv("CLIPADOR_FFMPEG_EXECUTABLE", "ffmpeg"),
            ffmpeg_timeout_seconds=int(os.getenv("CLIPADOR_FFMPEG_TIMEOUT_SECONDS", "7200")),
            max_audio_bytes=int(os.getenv("CLIPADOR_MAX_NORMALIZED_AUDIO_BYTES", "1073741824")),
            max_transcript_bytes=int(os.getenv("CLIPADOR_TRANSCRIPT_MAX_ARTIFACT_BYTES", "67108864")),
            whisper_model=os.getenv("CLIPADOR_WHISPER_MODEL", "small"),
            whisper_device=os.getenv("CLIPADOR_WHISPER_DEVICE", "cpu"),
            whisper_compute_type=os.getenv("CLIPADOR_WHISPER_COMPUTE_TYPE", "int8"),
            whisper_beam_size=int(os.getenv("CLIPADOR_WHISPER_BEAM_SIZE", "5")),
            whisper_vad_min_silence_ms=int(os.getenv("CLIPADOR_WHISPER_VAD_MIN_SILENCE_MS", "500")),
            whisper_model_cache=Path(os.getenv("CLIPADOR_WHISPER_MODEL_CACHE", "./data/worker/models")),
            default_language=(os.getenv("CLIPADOR_WHISPER_LANGUAGE") or "").strip().lower() or None,
            max_analysis_bytes=int(os.getenv("CLIPADOR_ANALYSIS_MAX_ARTIFACT_BYTES", "16777216")),
            analysis_visual_fps=float(os.getenv("CLIPADOR_ANALYSIS_VISUAL_FPS", "1.0")),
            analysis_visual_width=int(os.getenv("CLIPADOR_ANALYSIS_VISUAL_WIDTH", "64")),
            analysis_visual_height=int(os.getenv("CLIPADOR_ANALYSIS_VISUAL_HEIGHT", "36")),
            analysis_provider=os.getenv("CLIPADOR_ANALYSIS_PROVIDER", "local").strip().lower(),
            ollama_url=os.getenv("CLIPADOR_OLLAMA_URL", "http://127.0.0.1:11434/api/chat").strip(),
            ollama_model=os.getenv("CLIPADOR_OLLAMA_MODEL", "qwen3:4b").strip(),
            ollama_timeout_seconds=int(os.getenv("CLIPADOR_OLLAMA_TIMEOUT_SECONDS", "180")),
            ollama_max_candidates=int(os.getenv("CLIPADOR_OLLAMA_MAX_CANDIDATES", "20")),
            max_render_manifest_bytes=int(os.getenv("CLIPADOR_RENDER_MAX_MANIFEST_BYTES", "16777216")),
            max_render_output_bytes=int(os.getenv("CLIPADOR_RENDER_MAX_OUTPUT_BYTES", "2147483648")),
            smart_reframing_enabled=boolean_env("CLIPADOR_SMART_REFRAMING_ENABLED", True),
            reframing_mode=os.getenv("CLIPADOR_REFRAMING_MODE", "AUTO").strip().upper(),
            reframing_sample_fps=float(os.getenv("CLIPADOR_REFRAMING_SAMPLE_FPS", "1.5")),
            reframing_smoothing=float(os.getenv("CLIPADOR_REFRAMING_SMOOTHING", "0.82")),
            reframing_max_pan_ratio_per_second=float(
                os.getenv("CLIPADOR_REFRAMING_MAX_PAN_RATIO_PER_SECOND", "0.35")),
            reframing_face_min_size_ratio=float(
                os.getenv("CLIPADOR_REFRAMING_FACE_MIN_SIZE_RATIO", "0.025")),
            reframing_detection_width=int(os.getenv("CLIPADOR_REFRAMING_DETECTION_WIDTH", "640")),
            reframing_max_keyframes=int(os.getenv("CLIPADOR_REFRAMING_MAX_KEYFRAMES", "64")),
            enabled_tasks=tuple(task.strip().upper() for task in
                                os.getenv("CLIPADOR_WORKER_TASKS",
                                          "VALIDATE_MEDIA,EXTRACT_AUDIO,TRANSCRIBE_AUDIO,ANALYZE_CONTENT,RENDER_CLIPS").split(",")
                                if task.strip()),
        )
        if settings.max_retries < 0 or settings.max_retries > 20:
            raise ValueError("CLIPADOR_WORKER_MAX_RETRIES must be between 0 and 20")
        if not 1 <= settings.rabbit_port <= 65535:
            raise ValueError("RABBITMQ_PORT must be between 1 and 65535")
        if settings.retry_delay_ms < 1000:
            raise ValueError("CLIPADOR_RABBIT_RETRY_DELAY_MS must be at least 1000")
        if not 1 <= settings.reconnect_max_seconds <= 300:
            raise ValueError("CLIPADOR_WORKER_RECONNECT_MAX_SECONDS must be between 1 and 300")
        if not 1 <= settings.ffmpeg_timeout_seconds <= 86_400:
            raise ValueError("CLIPADOR_FFMPEG_TIMEOUT_SECONDS must be between 1 and 86400")
        if not 1 <= settings.whisper_beam_size <= 20:
            raise ValueError("CLIPADOR_WHISPER_BEAM_SIZE must be between 1 and 20")
        if not 0 <= settings.whisper_vad_min_silence_ms <= 10_000:
            raise ValueError("CLIPADOR_WHISPER_VAD_MIN_SILENCE_MS must be between 0 and 10000")
        if settings.max_audio_bytes < 1_048_576 or settings.max_transcript_bytes < 1024:
            raise ValueError("Worker artifact limits are too small")
        supported = {"VALIDATE_MEDIA", "EXTRACT_AUDIO", "TRANSCRIBE_AUDIO", "ANALYZE_CONTENT", "RENDER_CLIPS"}
        if not settings.enabled_tasks or not set(settings.enabled_tasks).issubset(supported):
            raise ValueError("CLIPADOR_WORKER_TASKS contains an unsupported task")
        if settings.default_language is not None and (
                len(settings.default_language) != 2 or not settings.default_language.isalpha()):
            raise ValueError("CLIPADOR_WHISPER_LANGUAGE must be a two-letter Whisper language code")
        if settings.whisper_device not in {"cpu", "cuda", "auto"}:
            raise ValueError("CLIPADOR_WHISPER_DEVICE must be cpu, cuda or auto")
        if settings.max_analysis_bytes < 1024 or not 0.1 <= settings.analysis_visual_fps <= 5:
            raise ValueError("Analysis artifact limit or visual sampling rate is invalid")
        if not 16 <= settings.analysis_visual_width <= 320 or not 16 <= settings.analysis_visual_height <= 180:
            raise ValueError("Analysis visual dimensions are invalid")
        if settings.analysis_provider not in {"local", "ollama"}:
            raise ValueError("CLIPADOR_ANALYSIS_PROVIDER must be local or ollama")
        parsed_ollama = urlparse(settings.ollama_url)
        if (parsed_ollama.scheme != "http" or parsed_ollama.hostname not in {"localhost", "127.0.0.1", "::1"}
                or parsed_ollama.username or parsed_ollama.password or parsed_ollama.path != "/api/chat"):
            raise ValueError("CLIPADOR_OLLAMA_URL must be a loopback HTTP /api/chat endpoint")
        if not settings.ollama_model or not 10 <= settings.ollama_timeout_seconds <= 1800:
            raise ValueError("Ollama model or timeout is invalid")
        if not 1 <= settings.ollama_max_candidates <= 100:
            raise ValueError("CLIPADOR_OLLAMA_MAX_CANDIDATES must be between 1 and 100")
        if settings.max_render_manifest_bytes < 1024 or settings.max_render_output_bytes < 1_048_576:
            raise ValueError("Render artifact limits are invalid")
        if settings.reframing_mode not in {"AUTO", "FOCUS", "GROUP", "BLURRED_BACKGROUND"}:
            raise ValueError("CLIPADOR_REFRAMING_MODE is invalid")
        if not 0.25 <= settings.reframing_sample_fps <= 5:
            raise ValueError("CLIPADOR_REFRAMING_SAMPLE_FPS must be between 0.25 and 5")
        if not 0 <= settings.reframing_smoothing <= 0.98:
            raise ValueError("CLIPADOR_REFRAMING_SMOOTHING must be between 0 and 0.98")
        if not 0.05 <= settings.reframing_max_pan_ratio_per_second <= 1:
            raise ValueError("CLIPADOR_REFRAMING_MAX_PAN_RATIO_PER_SECOND must be between 0.05 and 1")
        if not 0.005 <= settings.reframing_face_min_size_ratio <= 0.25:
            raise ValueError("CLIPADOR_REFRAMING_FACE_MIN_SIZE_RATIO must be between 0.005 and 0.25")
        if not 160 <= settings.reframing_detection_width <= 1280:
            raise ValueError("CLIPADOR_REFRAMING_DETECTION_WIDTH must be between 160 and 1280")
        if not 2 <= settings.reframing_max_keyframes <= 256:
            raise ValueError("CLIPADOR_REFRAMING_MAX_KEYFRAMES must be between 2 and 256")
        return settings


def required(name: str) -> str:
    value = os.getenv(name)
    if value is None or not value.strip():
        raise RuntimeError(f"Required environment variable {name} is missing")
    return value


def boolean_env(name: str, default: bool) -> bool:
    raw = os.getenv(name)
    if raw is None:
        return default
    normalized = raw.strip().lower()
    if normalized in {"1", "true", "yes", "on"}:
        return True
    if normalized in {"0", "false", "no", "off"}:
        return False
    raise ValueError(f"{name} must be a boolean")
