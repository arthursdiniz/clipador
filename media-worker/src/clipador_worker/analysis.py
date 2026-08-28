from __future__ import annotations

from array import array
from dataclasses import dataclass
import hashlib
import json
import math
from pathlib import Path
import re
import statistics
import subprocess
from typing import Protocol
from typing import Literal
from urllib import error as url_error
from urllib import request as url_request
import wave

from pydantic import BaseModel, ConfigDict, Field

from .artifacts import JobArtifactStorage
from .audio import MediaTaskExecutionError
from .config import Settings
from .contracts import AnalyzeContentCommandV1
from .storage import LocalMediaValidator


class ClipAnalysisProvider(Protocol):
    def analyze(self, command: AnalyzeContentCommandV1) -> dict[str, object]: ...


@dataclass(frozen=True)
class Segment:
    start: float
    end: float
    text: str


@dataclass(frozen=True)
class Timeline:
    step: float
    values: tuple[float, ...]

    def window(self, start: float, end: float) -> tuple[float, ...]:
        first = max(0, int(start / self.step))
        last = min(len(self.values), max(first + 1, math.ceil(end / self.step)))
        return self.values[first:last]


class LocalMultimodalClipAnalyzer:
    provider_name = "local-multimodal-heuristic-v1"

    def __init__(self, settings: Settings, artifacts: JobArtifactStorage,
                 validator: LocalMediaValidator) -> None:
        self._settings = settings
        self._artifacts = artifacts
        self._validator = validator

    def analyze(self, command: AnalyzeContentCommandV1) -> dict[str, object]:
        transcript_path = self._artifacts.resolve_input(command.transcript_storage_key, command.job_id)
        audio_path = self._artifacts.resolve_input(command.audio_storage_key, command.job_id)
        video_path = self._validator.resolve_video(command.video_storage_key, command.video_id)
        target = self._artifacts.output_target(command.analysis_storage_key, command.job_id)
        existing = self._existing_details(target, command)
        if existing is not None:
            return existing

        transcript = self._read_transcript(transcript_path, command)
        segments = self._segments(transcript)
        audio = audio_timeline(audio_path)
        visual = visual_timeline(video_path, self._settings)
        windows = candidate_windows(segments, command.min_duration_seconds,
                                    command.ideal_duration_seconds, command.max_duration_seconds)
        candidates = [score_window(segments, start, end, audio, visual, command)
                      for start, end in windows]
        candidates.sort(key=lambda item: (-float(item["finalScore"]), float(item["start"])))
        candidates = candidates[:command.max_candidates]
        if not candidates:
            raise MediaTaskExecutionError(
                "NO_CLIP_CANDIDATES",
                "The transcript has no complete passage inside the configured duration range",
                False,
            )
        candidates = self._enrich(candidates, command)
        candidates.sort(key=lambda item: (-float(item["finalScore"]), float(item["start"])))

        document = {
            "schemaVersion": 1,
            "jobId": str(command.job_id),
            "videoId": str(command.video_id),
            "provider": self.provider_name,
            "signals": {"text": True, "audio": bool(audio.values), "visual": bool(visual.values)},
            "weights": {
                "semantic": command.semantic_weight,
                "audio": command.audio_weight,
                "visual": command.visual_weight,
                "narrative": command.narrative_weight,
                "hook": command.hook_weight,
                "contextPenalty": command.context_penalty_weight,
            },
            "candidates": candidates,
        }
        temporary = self._artifacts.temporary(target, command.message_id)
        temporary.unlink(missing_ok=True)
        try:
            with temporary.open("x", encoding="utf-8") as stream:
                json.dump(document, stream, ensure_ascii=False, separators=(",", ":"), allow_nan=False)
            if temporary.stat().st_size > self._settings.max_analysis_bytes:
                raise MediaTaskExecutionError("ANALYSIS_ARTIFACT_TOO_LARGE", "Analysis exceeds limit", False)
            self._artifacts.commit(temporary, target)
        finally:
            temporary.unlink(missing_ok=True)
        return self._details(command, target, len(candidates), reused=False)

    def _enrich(self, candidates: list[dict[str, object]],
                command: AnalyzeContentCommandV1) -> list[dict[str, object]]:
        return candidates

    def _read_transcript(self, path: Path, command: AnalyzeContentCommandV1) -> dict[str, object]:
        try:
            with path.open("r", encoding="utf-8") as stream:
                document = json.load(stream)
        except (OSError, ValueError, TypeError) as exc:
            raise MediaTaskExecutionError("INVALID_TRANSCRIPT_ARTIFACT", str(exc), False) from exc
        if (document.get("schemaVersion") != 1 or document.get("jobId") != str(command.job_id)
                or document.get("videoId") != str(command.video_id)):
            raise MediaTaskExecutionError("INVALID_TRANSCRIPT_ARTIFACT", "Transcript envelope does not match", False)
        return document

    def _segments(self, transcript: dict[str, object]) -> list[Segment]:
        output: list[Segment] = []
        for raw in transcript.get("segments", []):
            try:
                segment = Segment(float(raw["start"]), float(raw["end"]), normalize_text(str(raw["text"])))
            except (KeyError, TypeError, ValueError) as exc:
                raise MediaTaskExecutionError("INVALID_TRANSCRIPT_ARTIFACT", "Invalid transcript segment", False) from exc
            if not segment.text or segment.start < 0 or segment.end <= segment.start:
                raise MediaTaskExecutionError("INVALID_TRANSCRIPT_ARTIFACT", "Invalid transcript segment", False)
            if output and segment.start < output[-1].start:
                raise MediaTaskExecutionError("INVALID_TRANSCRIPT_ARTIFACT", "Transcript is not ordered", False)
            output.append(segment)
        if not output:
            raise MediaTaskExecutionError("INVALID_TRANSCRIPT_ARTIFACT", "Transcript has no segments", False)
        return output

    def _existing_details(self, target: Path, command: AnalyzeContentCommandV1) -> dict[str, object] | None:
        if not target.is_file() or target.stat().st_size <= 0:
            return None
        try:
            with target.open("r", encoding="utf-8") as stream:
                document = json.load(stream)
            if document.get("schemaVersion") != 1 or document.get("jobId") != str(command.job_id):
                return None
            return self._details(command, target, len(document.get("candidates", [])), reused=True)
        except (OSError, ValueError, TypeError):
            return None

    def _details(self, command: AnalyzeContentCommandV1, target: Path,
                 count: int, reused: bool) -> dict[str, object]:
        return {"analysisStorageKey": command.analysis_storage_key, "candidateCount": count,
                "sizeBytes": target.stat().st_size, "provider": self.provider_name,
                "reused": reused}


class LlmCandidateScore(BaseModel):
    model_config = ConfigDict(extra="forbid")

    candidate_key: str = Field(alias="candidateKey", min_length=1, max_length=64)
    semantic_score: float = Field(alias="semanticScore", ge=0, le=1)
    narrative_score: float = Field(alias="narrativeScore", ge=0, le=1)
    hook_score: float = Field(alias="hookScore", ge=0, le=1)
    context_penalty: float = Field(alias="contextPenalty", ge=0, le=1)
    reason: str = Field(min_length=1, max_length=1000)
    hook: str = Field(min_length=1, max_length=500)
    category: Literal["STORY", "INSIGHT", "HUMOR", "CONFLICT", "EMOTION", "TIP",
                      "OPINION", "QUESTION_ANSWER", "REVELATION", "OTHER"]


class LlmCandidateBatch(BaseModel):
    model_config = ConfigDict(extra="forbid")
    candidates: list[LlmCandidateScore]


class OllamaClipAnalysisProvider(LocalMultimodalClipAnalyzer):
    provider_name = "ollama-structured-multimodal-v1"

    def _enrich(self, candidates: list[dict[str, object]],
                command: AnalyzeContentCommandV1) -> list[dict[str, object]]:
        requested = candidates[:self._settings.ollama_max_candidates]
        prompt_candidates = [{"candidateKey": item["candidateKey"], "start": item["start"],
                              "end": item["end"], "text": item["sourceText"]}
                             for item in requested]
        prompt = (
            "Você seleciona cortes curtos autossuficientes de podcasts e entrevistas em português ou inglês. "
            "Avalie cada candidato sem alterar candidateKey. Penalize começo no meio da ideia, dependência de "
            "contexto e final sem conclusão. Valorize hook imediato, clareza, história/argumento completo e "
            "potencial de retenção. Retorne somente o JSON exigido pelo schema. Candidatos: "
            + json.dumps(prompt_candidates, ensure_ascii=False, separators=(",", ":"))
        )
        payload = {
            "model": self._settings.ollama_model,
            "messages": [{"role": "user", "content": prompt}],
            "stream": False,
            "think": False,
            "format": LlmCandidateBatch.model_json_schema(),
            "options": {"temperature": 0},
        }
        response = self._request(payload)
        try:
            content = response["message"]["content"]
            batch = LlmCandidateBatch.model_validate_json(content)
        except (KeyError, TypeError, ValueError) as exc:
            raise MediaTaskExecutionError("LLM_INVALID_STRUCTURED_OUTPUT", str(exc), False) from exc
        expected = {str(item["candidateKey"]) for item in requested}
        received = {item.candidate_key for item in batch.candidates}
        if expected != received or len(received) != len(batch.candidates):
            raise MediaTaskExecutionError("LLM_INVALID_STRUCTURED_OUTPUT",
                                          "Ollama returned missing or duplicate candidate keys", False)
        scores = {item.candidate_key: item for item in batch.candidates}
        enriched: list[dict[str, object]] = []
        for candidate in candidates:
            score = scores.get(str(candidate["candidateKey"]))
            if score is not None:
                candidate = apply_llm_score(candidate, score, command)
            enriched.append(candidate)
        return enriched

    def _request(self, payload: dict[str, object]) -> dict[str, object]:
        body = json.dumps(payload, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
        request = url_request.Request(self._settings.ollama_url, data=body,
                                      headers={"Content-Type": "application/json"}, method="POST")
        try:
            with url_request.urlopen(request, timeout=self._settings.ollama_timeout_seconds) as response:
                if response.status != 200:
                    raise MediaTaskExecutionError("OLLAMA_REQUEST_FAILED",
                                                  f"Ollama returned HTTP {response.status}", True)
                return json.loads(response.read(16_777_216).decode("utf-8"))
        except MediaTaskExecutionError:
            raise
        except (url_error.URLError, TimeoutError, OSError, ValueError) as exc:
            raise MediaTaskExecutionError("OLLAMA_REQUEST_FAILED", str(exc), True) from exc


def apply_llm_score(candidate: dict[str, object], score: LlmCandidateScore,
                    command: AnalyzeContentCommandV1) -> dict[str, object]:
    positive_weight = (command.semantic_weight + command.audio_weight + command.visual_weight
                       + command.narrative_weight + command.hook_weight)
    positive = (score.semantic_score * command.semantic_weight
                + float(candidate["audioScore"]) * command.audio_weight
                + float(candidate["visualScore"]) * command.visual_weight
                + score.narrative_score * command.narrative_weight
                + score.hook_score * command.hook_weight) / max(positive_weight, 0.0001)
    updated = dict(candidate)
    updated.update({
        "semanticScore": round(score.semantic_score, 5),
        "narrativeScore": round(score.narrative_score, 5),
        "hookScore": round(score.hook_score, 5),
        "contextPenalty": round(score.context_penalty, 5),
        "finalScore": round(clamp(positive - score.context_penalty * command.context_penalty_weight), 5),
        "reason": score.reason,
        "hook": score.hook,
        "category": score.category,
    })
    return updated


def candidate_windows(segments: list[Segment], minimum: float, ideal: float,
                      maximum: float) -> list[tuple[int, int]]:
    windows: set[tuple[int, int]] = set()
    for start_index, segment in enumerate(segments):
        if not start_boundary(segments, start_index):
            continue
        possible: list[tuple[float, int]] = []
        for end_index in range(start_index, len(segments)):
            duration = segments[end_index].end - segment.start
            if duration > maximum + 0.001:
                break
            if duration >= minimum and end_boundary(segments, end_index):
                boundary_bonus = 0 if terminal(segments[end_index].text) else 8
                possible.append((abs(duration - ideal) + boundary_bonus, end_index))
        for _, end_index in sorted(possible)[:3]:
            windows.add((start_index, end_index))
    return sorted(windows)


def start_boundary(segments: list[Segment], index: int) -> bool:
    if index == 0:
        return not continuation_start(segments[index].text)
    gap = segments[index].start - segments[index - 1].end
    return (gap >= 0.45 or terminal(segments[index - 1].text)) and not continuation_start(segments[index].text)


def end_boundary(segments: list[Segment], index: int) -> bool:
    if index == len(segments) - 1:
        return True
    gap = segments[index + 1].start - segments[index].end
    return gap >= 0.55 or terminal(segments[index].text)


def continuation_start(text: str) -> bool:
    first = normalize_token(text.split(maxsplit=1)[0] if text else "")
    return first in {"e", "mas", "porque", "então", "daí", "ele", "ela", "isso", "and", "but",
                     "because", "so", "then", "he", "she", "it", "they", "also"}


def terminal(text: str) -> bool:
    return bool(re.search(r"[.!?…][\"')\]]?$", text.strip()))


def score_window(segments: list[Segment], start_index: int, end_index: int,
                 audio: Timeline, visual: Timeline,
                 command: AnalyzeContentCommandV1) -> dict[str, object]:
    selected = segments[start_index:end_index + 1]
    start, end = selected[0].start, selected[-1].end
    text = normalize_text(" ".join(segment.text for segment in selected))
    hook_segments = [segment for segment in selected if segment.start < start + 8]
    hook = normalize_text(" ".join(segment.text for segment in hook_segments))[:500]
    semantic, category = semantic_score(text)
    narrative = narrative_score(selected)
    hook_value = hook_score(hook)
    context = context_penalty(selected)
    audio_value = timeline_interest(audio.window(start, end), neutral=0.45)
    visual_value = timeline_interest(visual.window(start, end), neutral=0.40)
    positive_weight = (command.semantic_weight + command.audio_weight + command.visual_weight
                       + command.narrative_weight + command.hook_weight)
    positive = (semantic * command.semantic_weight + audio_value * command.audio_weight
                + visual_value * command.visual_weight + narrative * command.narrative_weight
                + hook_value * command.hook_weight) / max(positive_weight, 0.0001)
    final = clamp(positive - context * command.context_penalty_weight)
    key_source = f"{start:.3f}:{end:.3f}:{text}".encode("utf-8")
    reasons = []
    if hook_value >= 0.65: reasons.append("abertura forte")
    if narrative >= 0.65: reasons.append("ideia completa")
    if audio_value >= 0.62: reasons.append("áudio expressivo")
    if visual_value >= 0.62: reasons.append("variação visual")
    if not reasons: reasons.append("trecho autossuficiente")
    return {
        "candidateKey": hashlib.sha256(key_source).hexdigest()[:32],
        "start": round(start, 3), "end": round(end, 3),
        "semanticScore": round(semantic, 5), "audioScore": round(audio_value, 5),
        "visualScore": round(visual_value, 5), "narrativeScore": round(narrative, 5),
        "hookScore": round(hook_value, 5), "contextPenalty": round(context, 5),
        "finalScore": round(final, 5),
        "reason": "Trecho com " + ", ".join(reasons) + ".",
        "hook": hook, "category": category, "sourceText": text[:50_000],
    }


def semantic_score(text: str) -> tuple[float, str]:
    tokens = [normalize_token(token) for token in re.findall(r"[^\W_]+|\d+", text.lower(), re.UNICODE)]
    token_set = set(tokens)
    categories = {
        "STORY": {"aconteceu", "lembro", "quando", "história", "story", "happened", "remember"},
        "INSIGHT": {"percebi", "aprendi", "verdade", "segredo", "realize", "learned", "truth", "secret"},
        "HUMOR": {"engraçado", "risada", "piada", "funny", "laugh", "joke"},
        "CONFLICT": {"erro", "problema", "discordo", "contra", "wrong", "problem", "disagree"},
        "EMOTION": {"medo", "amor", "ódio", "feliz", "triste", "fear", "love", "happy", "sad"},
        "TIP": {"dica", "passo", "faça", "evite", "como", "tip", "step", "avoid", "how"},
        "OPINION": {"acredito", "opinião", "penso", "acho", "believe", "opinion", "think"},
        "QUESTION_ANSWER": {"porquê", "por", "como", "why", "how", "what"},
        "REVELATION": {"descobri", "revelar", "ninguém", "surpresa", "discovered", "nobody", "surprise"},
    }
    hits = {name: len(words & token_set) for name, words in categories.items()}
    category = max(hits, key=hits.get) if max(hits.values(), default=0) else "OTHER"
    keyword_signal = min(1.0, max(hits.values(), default=0) / 3)
    lexical = min(1.0, len(token_set) / max(20, len(tokens) * 0.55))
    impact = min(1.0, (text.count("?") + text.count("!") + sum(token.isdigit() for token in tokens)) / 4)
    return clamp(0.35 + 0.30 * keyword_signal + 0.20 * lexical + 0.15 * impact), category


def narrative_score(segments: list[Segment]) -> float:
    text = " ".join(segment.text for segment in segments)
    start_quality = 0.75 if not continuation_start(segments[0].text) else 0.2
    end_quality = 1.0 if terminal(segments[-1].text) else 0.45
    markers = sum(marker in text.lower() for marker in (
        "porque", "por isso", "então", "resultado", "conclusão", "because", "therefore", "result", "finally"))
    progression = min(1.0, 0.35 + markers * 0.2)
    words = len(text.split())
    density = clamp(words / max(1.0, (segments[-1].end - segments[0].start) * 2.2))
    return clamp(0.30 * start_quality + 0.35 * end_quality + 0.20 * progression + 0.15 * density)


def hook_score(text: str) -> float:
    lowered = text.lower()
    value = 0.30
    if "?" in text: value += 0.25
    if "!" in text: value += 0.10
    if re.search(r"\d", text): value += 0.12
    if any(word in lowered for word in (
            "segredo", "ninguém", "nunca", "descobri", "perdi", "erro", "como", "por que",
            "secret", "nobody", "never", "discovered", "lost", "mistake", "how", "why")):
        value += 0.32
    if continuation_start(text): value -= 0.30
    return clamp(value)


def context_penalty(segments: list[Segment]) -> float:
    text = segments[0].text.lower()
    penalty = 0.05
    if continuation_start(text): penalty += 0.45
    if re.match(r"^(isso|isto|aquilo|ele|ela|eles|elas|it|this|that|he|she|they)\b", text): penalty += 0.25
    gaps = [segments[index + 1].start - segments[index].end for index in range(len(segments) - 1)]
    if gaps:
        penalty += min(0.25, sum(gap > 2.0 for gap in gaps) / len(gaps) * 0.5)
    if not terminal(segments[-1].text): penalty += 0.20
    return clamp(penalty)


def timeline_interest(values: tuple[float, ...], neutral: float) -> float:
    if not values:
        return neutral
    mean = statistics.fmean(values)
    variation = statistics.pstdev(values) if len(values) > 1 else 0
    active = sum(value >= 0.15 for value in values) / len(values)
    return clamp(0.50 * mean + 0.25 * min(1.0, variation * 3) + 0.25 * active)


def audio_timeline(path: Path, step: float = 0.5) -> Timeline:
    try:
        with wave.open(str(path), "rb") as source:
            if source.getsampwidth() != 2 or source.getnchannels() != 1:
                raise MediaTaskExecutionError("INVALID_NORMALIZED_AUDIO", "Expected mono PCM 16-bit WAV", False)
            frames_per_step = max(1, int(source.getframerate() * step))
            raw_values: list[float] = []
            while data := source.readframes(frames_per_step):
                samples = array("h")
                samples.frombytes(data)
                if not samples:
                    continue
                energy = math.sqrt(sum(sample * sample for sample in samples) / len(samples)) / 32768.0
                raw_values.append(energy)
    except (wave.Error, OSError) as exc:
        raise MediaTaskExecutionError("INVALID_NORMALIZED_AUDIO", str(exc), False) from exc
    reference = percentile(raw_values, 0.95) or 1.0
    return Timeline(step, tuple(clamp(value / reference) for value in raw_values))


def visual_timeline(path: Path, settings: Settings) -> Timeline:
    frame_size = settings.analysis_visual_width * settings.analysis_visual_height
    command = [
        settings.ffmpeg_executable, "-nostdin", "-hide_banner", "-loglevel", "error", "-i", str(path),
        "-an", "-sn", "-dn", "-vf",
        f"fps={settings.analysis_visual_fps},scale={settings.analysis_visual_width}:{settings.analysis_visual_height}",
        "-pix_fmt", "gray", "-f", "rawvideo", "pipe:1",
    ]
    try:
        completed = subprocess.run(command, stdin=subprocess.DEVNULL, stdout=subprocess.PIPE,
                                   stderr=subprocess.PIPE, timeout=settings.ffmpeg_timeout_seconds, check=False)
    except subprocess.TimeoutExpired as exc:
        raise MediaTaskExecutionError("VISUAL_ANALYSIS_TIMEOUT", "Visual sampling timed out", True) from exc
    except OSError as exc:
        raise MediaTaskExecutionError("FFMPEG_UNAVAILABLE", str(exc), True) from exc
    if completed.returncode != 0:
        error = completed.stderr.decode("utf-8", errors="replace")[-4000:]
        raise MediaTaskExecutionError("VISUAL_ANALYSIS_FAILED", error, False)
    frames = [completed.stdout[offset:offset + frame_size]
              for offset in range(0, len(completed.stdout) - frame_size + 1, frame_size)]
    values: list[float] = [0.0] if frames else []
    for previous, current in zip(frames, frames[1:]):
        difference = sum(abs(a - b) for a, b in zip(previous, current)) / (frame_size * 255)
        values.append(clamp(difference * 5))
    return Timeline(1.0 / settings.analysis_visual_fps, tuple(values))


def percentile(values: list[float], ratio: float) -> float:
    if not values:
        return 0.0
    ordered = sorted(values)
    return ordered[min(len(ordered) - 1, int((len(ordered) - 1) * ratio))]


def normalize_text(text: str) -> str:
    return " ".join(text.split())


def normalize_token(token: str) -> str:
    return token.lower().strip(".,!?;:()[]{}\"'…")


def clamp(value: float) -> float:
    return max(0.0, min(1.0, value))
