from __future__ import annotations

from dataclasses import dataclass
import json
import logging
from pathlib import Path
import re
import subprocess

from .artifacts import JobArtifactStorage
from .audio import MediaTaskExecutionError
from .config import Settings
from .contracts import (RenderCandidateSpec, RenderClipsCommandV1, RenderClipsCommandV2,
                        RenderFormatSpec)
from .reframing import CropKeyframe, ReframePlan, SmartReframePlanner
from .storage import LocalMediaValidator

logger = logging.getLogger(__name__)


@dataclass(frozen=True)
class CaptionWord:
    start: float
    end: float
    text: str


@dataclass(frozen=True)
class CaptionCue:
    start: float
    end: float
    words: tuple[CaptionWord, ...]

    @property
    def text(self) -> str:
        return " ".join(word.text for word in self.words)


class FfmpegClipRenderer:
    def __init__(self, settings: Settings, artifacts: JobArtifactStorage,
                 validator: LocalMediaValidator) -> None:
        self._settings = settings
        self._artifacts = artifacts
        self._validator = validator
        self._reframing = SmartReframePlanner()

    def render(self, command: RenderClipsCommandV1 | RenderClipsCommandV2) -> dict[str, object]:
        source = self._validator.resolve_video(command.video_storage_key, command.video_id)
        transcript_path = self._artifacts.resolve_input(command.transcript_storage_key, command.job_id)
        transcript = self._read_transcript(transcript_path, command)
        manifest_target = self._artifacts.output_target(command.manifest_storage_key, command.job_id)
        existing = self._existing_manifest(manifest_target, command)
        if existing is not None:
            return existing

        renders: list[dict[str, object]] = []
        for candidate in command.candidates:
            cues = caption_cues(transcript, candidate.start, candidate.end)
            for format_spec in command.formats:
                try:
                    renders.append(self._render_one(source, candidate, format_spec, cues, command))
                except MediaTaskExecutionError as exc:
                    if exc.retryable:
                        raise
                    renders.append(failed_render(candidate, format_spec, exc.code, str(exc)))
                except (OSError, ValueError) as exc:
                    renders.append(failed_render(candidate, format_spec, "CLIP_RENDER_FAILED", str(exc)))

        document = {"schemaVersion": 1, "jobId": str(command.job_id),
                    "videoId": str(command.video_id), "renders": renders}
        self._write_json(manifest_target, document, command)
        succeeded = sum(item["status"] == "SUCCEEDED" for item in renders)
        return {"manifestStorageKey": command.manifest_storage_key,
                "succeededCount": succeeded, "failedCount": len(renders) - succeeded,
                "sizeBytes": manifest_target.stat().st_size, "reused": False}

    def _render_one(self, source: Path, candidate: RenderCandidateSpec,
                    format_spec: RenderFormatSpec, cues: list[CaptionCue],
                    command: RenderClipsCommandV1 | RenderClipsCommandV2) -> dict[str, object]:
        token = format_spec.format.lower()
        prefix = f"jobs/{command.job_id}/clips/{candidate.candidate_id}/{token}"
        keys = {"video": f"{prefix}/clip.mp4", "srt": f"{prefix}/subtitles.srt",
                "vtt": f"{prefix}/subtitles.vtt", "ass": f"{prefix}/subtitles.ass",
                "thumbnail": f"{prefix}/thumbnail.jpg"}
        targets = {name: self._artifacts.output_target(key, command.job_id) for name, key in keys.items()}
        if not cues:
            raise MediaTaskExecutionError("NO_SUBTITLES_FOR_CLIP",
                                          "No transcript words overlap this candidate", False)
        self._write_subtitles(targets, cues, format_spec, command)
        reframe_plan = self._reframe_plan(source, candidate, format_spec, command)

        if not valid_mp4(targets["video"]):
            temporary = self._artifacts.temporary(targets["video"], command.message_id)
            temporary.unlink(missing_ok=True)
            try:
                self._run(render_command(self._settings.ffmpeg_executable, source, temporary,
                                         targets["ass"], candidate, format_spec, command,
                                         reframe_plan),
                          "CLIP_RENDER_FAILED")
                if not valid_mp4(temporary):
                    raise MediaTaskExecutionError("INVALID_RENDERED_CLIP",
                                                  "FFmpeg produced an invalid MP4", False)
                if temporary.stat().st_size > self._settings.max_render_output_bytes:
                    raise MediaTaskExecutionError("RENDERED_CLIP_TOO_LARGE",
                                                  "Rendered clip exceeds configured limit", False)
                self._artifacts.commit(temporary, targets["video"])
            finally:
                temporary.unlink(missing_ok=True)

        if not valid_jpeg(targets["thumbnail"]):
            temporary = self._artifacts.temporary(targets["thumbnail"], command.message_id)
            temporary.unlink(missing_ok=True)
            try:
                self._run(thumbnail_command(self._settings.ffmpeg_executable, source, temporary,
                                            candidate), "THUMBNAIL_GENERATION_FAILED")
                if not valid_jpeg(temporary):
                    raise MediaTaskExecutionError("INVALID_THUMBNAIL",
                                                  "FFmpeg produced an invalid thumbnail", False)
                self._artifacts.commit(temporary, targets["thumbnail"])
            finally:
                temporary.unlink(missing_ok=True)

        return {"candidateId": str(candidate.candidate_id), "format": format_spec.format,
                "status": "SUCCEEDED", "width": format_spec.width, "height": format_spec.height,
                "durationSeconds": round(candidate.end - candidate.start, 3),
                "storageKey": keys["video"], "srtStorageKey": keys["srt"],
                "vttStorageKey": keys["vtt"], "assStorageKey": keys["ass"],
                "thumbnailStorageKey": keys["thumbnail"], "errorCode": None, "errorMessage": None,
                "reframing": reframe_plan.manifest() if reframe_plan else legacy_reframing()}

    def _reframe_plan(self, source: Path, candidate: RenderCandidateSpec,
                      format_spec: RenderFormatSpec,
                      command: RenderClipsCommandV1 | RenderClipsCommandV2) -> ReframePlan | None:
        if not isinstance(command, RenderClipsCommandV2):
            return None
        try:
            return self._reframing.plan(source, candidate, format_spec, command)
        except (OSError, RuntimeError, ValueError) as exc:
            logger.warning("Smart reframing fell back to blurred background: %s", exc)
            return None

    def _write_subtitles(self, targets: dict[str, Path], cues: list[CaptionCue],
                         format_spec: RenderFormatSpec, command: RenderClipsCommandV1) -> None:
        content = {"srt": render_srt(cues), "vtt": render_vtt(cues),
                   "ass": render_ass(cues, format_spec.width, format_spec.height)}
        for name, text in content.items():
            target = targets[name]
            if target.is_file() and target.stat().st_size > 0:
                continue
            temporary = self._artifacts.temporary(target, command.message_id)
            temporary.unlink(missing_ok=True)
            try:
                with temporary.open("x", encoding="utf-8", newline="\n") as stream:
                    stream.write(text)
                self._artifacts.commit(temporary, target)
            finally:
                temporary.unlink(missing_ok=True)

    def _run(self, arguments: list[str], code: str) -> None:
        try:
            completed = subprocess.run(arguments, stdin=subprocess.DEVNULL, stdout=subprocess.DEVNULL,
                                       stderr=subprocess.PIPE, text=True,
                                       timeout=self._settings.ffmpeg_timeout_seconds, check=False)
        except subprocess.TimeoutExpired as exc:
            raise MediaTaskExecutionError(code + "_TIMEOUT", "FFmpeg operation timed out", True) from exc
        except OSError as exc:
            raise MediaTaskExecutionError("FFMPEG_UNAVAILABLE", str(exc), True) from exc
        if completed.returncode != 0:
            message = (completed.stderr or "FFmpeg operation failed")[-4000:]
            raise MediaTaskExecutionError(code, message, False)

    def _read_transcript(self, path: Path, command: RenderClipsCommandV1) -> dict[str, object]:
        try:
            with path.open("r", encoding="utf-8") as stream:
                document = json.load(stream)
        except (OSError, ValueError) as exc:
            raise MediaTaskExecutionError("INVALID_TRANSCRIPT_ARTIFACT", str(exc), False) from exc
        if document.get("schemaVersion") != 1 or document.get("jobId") != str(command.job_id):
            raise MediaTaskExecutionError("INVALID_TRANSCRIPT_ARTIFACT", "Transcript does not match job", False)
        return document

    def _write_json(self, target: Path, document: dict[str, object],
                    command: RenderClipsCommandV1) -> None:
        temporary = self._artifacts.temporary(target, command.message_id)
        temporary.unlink(missing_ok=True)
        try:
            with temporary.open("x", encoding="utf-8") as stream:
                json.dump(document, stream, ensure_ascii=False, separators=(",", ":"), allow_nan=False)
            if temporary.stat().st_size > self._settings.max_render_manifest_bytes:
                raise MediaTaskExecutionError("RENDER_MANIFEST_TOO_LARGE", "Render manifest exceeds limit", False)
            self._artifacts.commit(temporary, target)
        finally:
            temporary.unlink(missing_ok=True)

    def _existing_manifest(self, target: Path,
                           command: RenderClipsCommandV1) -> dict[str, object] | None:
        if not target.is_file() or target.stat().st_size <= 0:
            return None
        try:
            with target.open("r", encoding="utf-8") as stream:
                document = json.load(stream)
            if document.get("schemaVersion") != 1 or document.get("jobId") != str(command.job_id):
                return None
            renders = document.get("renders", [])
            succeeded = sum(item.get("status") == "SUCCEEDED" for item in renders)
            return {"manifestStorageKey": command.manifest_storage_key,
                    "succeededCount": succeeded, "failedCount": len(renders) - succeeded,
                    "sizeBytes": target.stat().st_size, "reused": True}
        except (OSError, ValueError, TypeError):
            return None


def caption_cues(transcript: dict[str, object], clip_start: float, clip_end: float) -> list[CaptionCue]:
    words: list[CaptionWord] = []
    for segment in transcript.get("segments", []):
        raw_words = segment.get("words") or []
        if raw_words:
            for raw in raw_words:
                start, end = float(raw["start"]), float(raw["end"])
                text = normalize_caption(str(raw["word"]))
                if text and end > clip_start and start < clip_end:
                    words.append(CaptionWord(max(0, start - clip_start),
                                             min(clip_end, end) - clip_start, text))
        else:
            words.extend(distribute_segment_words(segment, clip_start, clip_end))
    words.sort(key=lambda word: (word.start, word.end))
    cues: list[CaptionCue] = []
    current: list[CaptionWord] = []
    for word in words:
        proposed = " ".join(item.text for item in [*current, word])
        if current and (len(current) >= 7 or len(proposed) > 42 or word.end - current[0].start > 3.2):
            cues.append(CaptionCue(current[0].start, max(current[-1].end, current[0].start + .25),
                                   tuple(current)))
            current = []
        current.append(word)
    if current:
        cues.append(CaptionCue(current[0].start, max(current[-1].end, current[0].start + .25), tuple(current)))
    return cues


def distribute_segment_words(segment: dict[str, object], clip_start: float,
                             clip_end: float) -> list[CaptionWord]:
    start, end = float(segment["start"]), float(segment["end"])
    tokens = [normalize_caption(token) for token in str(segment.get("text", "")).split()]
    tokens = [token for token in tokens if token]
    if not tokens or end <= clip_start or start >= clip_end:
        return []
    step = (end - start) / len(tokens)
    return [CaptionWord(max(0, start + index * step - clip_start),
                        min(clip_end, start + (index + 1) * step) - clip_start, token)
            for index, token in enumerate(tokens)
            if start + (index + 1) * step > clip_start and start + index * step < clip_end]


def render_srt(cues: list[CaptionCue]) -> str:
    blocks = [f"{index}\n{srt_time(cue.start)} --> {srt_time(cue.end)}\n{cue.text}"
              for index, cue in enumerate(cues, 1)]
    return "\n\n".join(blocks) + "\n"


def render_vtt(cues: list[CaptionCue]) -> str:
    blocks = [f"{vtt_time(cue.start)} --> {vtt_time(cue.end)}\n{cue.text}" for cue in cues]
    return "WEBVTT\n\n" + "\n\n".join(blocks) + "\n"


def render_ass(cues: list[CaptionCue], width: int, height: int) -> str:
    font_size = max(34, round(height * .042))
    margin = max(40, round(height * .10))
    header = f"""[Script Info]
ScriptType: v4.00+
PlayResX: {width}
PlayResY: {height}
ScaledBorderAndShadow: yes

[V4+ Styles]
Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding
Style: Shorts,Arial,{font_size},&H00FFFFFF,&H0000D7FF,&H00101010,&H80000000,-1,0,0,0,100,100,0,0,1,3,1,2,70,70,{margin},1

[Events]
Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
"""
    events = []
    for cue in cues:
        karaoke = " ".join(f"{{\\kf{max(1, round((word.end - word.start) * 100))}}}{ass_escape(word.text)}"
                           for word in cue.words)
        events.append(f"Dialogue: 0,{ass_time(cue.start)},{ass_time(cue.end)},Shorts,,0,0,0,,{karaoke}")
    return header + "\n".join(events) + "\n"


def render_command(ffmpeg: str, source: Path, target: Path, ass_path: Path,
                   candidate: RenderCandidateSpec, format_spec: RenderFormatSpec,
                   command: RenderClipsCommandV1 | RenderClipsCommandV2,
                   reframe_plan: ReframePlan | None = None) -> list[str]:
    if reframe_plan is None or reframe_plan.strategy == "BLURRED_BACKGROUND":
        base = blurred_background_filter(format_spec, command.output_fps)
    else:
        base = smart_crop_filter(reframe_plan, format_spec, command.output_fps)
    if command.burn_in_subtitles:
        graph = base + f";[base]subtitles=filename='{escape_filter_path(ass_path)}'[v]"
    else:
        graph = base + ";[base]null[v]"
    return [ffmpeg, "-nostdin", "-hide_banner", "-loglevel", "error", "-y",
            "-ss", seconds(candidate.start), "-t", seconds(candidate.end - candidate.start),
            "-i", str(source), "-filter_complex", graph, "-map", "[v]", "-map", "0:a:0?",
            "-c:v", "libx264", "-preset", command.encoder_preset, "-crf", str(command.video_crf),
            "-profile:v", "high", "-pix_fmt", "yuv420p", "-c:a", "aac",
            "-b:a", f"{command.audio_bitrate_kbps}k", "-ar", "48000", "-ac", "2",
            "-movflags", "+faststart", "-f", "mp4", str(target)]


def blurred_background_filter(format_spec: RenderFormatSpec, output_fps: int) -> str:
    return (f"[0:v]split=2[bg][fg];[bg]scale={format_spec.width}:{format_spec.height}:"
            "force_original_aspect_ratio=increase,"
            f"crop={format_spec.width}:{format_spec.height},boxblur=20:2[bg2];"
            f"[fg]scale={format_spec.width}:{format_spec.height}:force_original_aspect_ratio=decrease[fg2];"
            "[bg2][fg2]overlay=(W-w)/2:(H-h)/2,setsar=1,"
            f"fps={output_fps}[base]")


def smart_crop_filter(plan: ReframePlan, format_spec: RenderFormatSpec,
                      output_fps: int) -> str:
    x_expression = keyframe_expression(plan.keyframes, "x")
    y_expression = keyframe_expression(plan.keyframes, "y")
    return (f"[0:v]setpts=PTS-STARTPTS,crop={plan.crop_width}:{plan.crop_height}:"
            f"x='{x_expression}':y='{y_expression}',"
            f"scale={format_spec.width}:{format_spec.height}:flags=lanczos,setsar=1,"
            f"fps={output_fps}[base]")


def keyframe_expression(keyframes: tuple[CropKeyframe, ...], axis: str) -> str:
    if not keyframes:
        return "0"
    value = str(getattr(keyframes[-1], axis))
    for left, right in reversed(list(zip(keyframes, keyframes[1:]))):
        start_value = getattr(left, axis)
        difference = getattr(right, axis) - start_value
        duration = max(.001, right.time - left.time)
        interpolation = (f"{start_value}+({difference})*(t-{left.time:.3f})/"
                         f"{duration:.3f}")
        value = f"if(lt(t\\,{right.time:.3f})\\,{interpolation}\\,{value})"
    return value


def thumbnail_command(ffmpeg: str, source: Path, target: Path,
                      candidate: RenderCandidateSpec) -> list[str]:
    return [ffmpeg, "-nostdin", "-hide_banner", "-loglevel", "error", "-y",
            "-ss", seconds(candidate.start), "-t", seconds(candidate.end - candidate.start),
            "-i", str(source), "-vf", "thumbnail=60,scale=720:-2", "-frames:v", "1",
            "-c:v", "mjpeg", "-q:v", "2", "-update", "1", "-f", "image2", str(target)]


def failed_render(candidate: RenderCandidateSpec, format_spec: RenderFormatSpec,
                  code: str, message: str) -> dict[str, object]:
    return {"candidateId": str(candidate.candidate_id), "format": format_spec.format,
            "status": "FAILED", "width": format_spec.width, "height": format_spec.height,
            "durationSeconds": round(candidate.end - candidate.start, 3),
            "storageKey": None, "srtStorageKey": None, "vttStorageKey": None,
            "assStorageKey": None, "thumbnailStorageKey": None,
            "errorCode": code[:100], "errorMessage": (message or "Rendering failed")[:4000],
            "reframing": None}


def legacy_reframing() -> dict[str, object]:
    return {"strategy": "BLURRED_BACKGROUND", "faceDetectionCoverage": 0,
            "subjectDetectionCoverage": 0, "keyframeCount": 0, "fallback": True}


def valid_mp4(path: Path) -> bool:
    if not path.is_file() or path.stat().st_size < 32:
        return False
    with path.open("rb") as stream:
        return b"ftyp" in stream.read(32)


def valid_jpeg(path: Path) -> bool:
    if not path.is_file() or path.stat().st_size < 4:
        return False
    with path.open("rb") as stream:
        return stream.read(2) == b"\xff\xd8"


def seconds(value: float) -> str:
    return f"{value:.3f}"


def srt_time(value: float) -> str:
    return timestamp(value, ",", 3)


def vtt_time(value: float) -> str:
    return timestamp(value, ".", 3)


def ass_time(value: float) -> str:
    total = max(0, round(value * 100))
    hours, remainder = divmod(total, 360_000)
    minutes, remainder = divmod(remainder, 6_000)
    seconds_value, centiseconds = divmod(remainder, 100)
    return f"{hours}:{minutes:02d}:{seconds_value:02d}.{centiseconds:02d}"


def timestamp(value: float, separator: str, decimals: int) -> str:
    factor = 10 ** decimals
    total = max(0, round(value * factor))
    hours, remainder = divmod(total, 3600 * factor)
    minutes, remainder = divmod(remainder, 60 * factor)
    seconds_value, fraction = divmod(remainder, factor)
    return f"{hours:02d}:{minutes:02d}:{seconds_value:02d}{separator}{fraction:0{decimals}d}"


def escape_filter_path(path: Path) -> str:
    value = path.resolve().as_posix()
    return value.replace("\\", "\\\\").replace(":", "\\:").replace("'", "\\'")


def normalize_caption(text: str) -> str:
    return " ".join(text.split()).strip()


def ass_escape(text: str) -> str:
    return re.sub(r"([{}\\])", r"\\\1", text).replace("\n", r"\N")
