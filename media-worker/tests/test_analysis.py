import json
import math
from datetime import datetime, timezone
from pathlib import Path
from uuid import uuid4
import wave

import clipador_worker.analysis as analysis_module
from clipador_worker.analysis import (LocalMultimodalClipAnalyzer, OllamaClipAnalysisProvider,
                                      Segment, Timeline,
                                      candidate_windows, score_window)
from clipador_worker.artifacts import JobArtifactStorage
from clipador_worker.config import Settings
from clipador_worker.contracts import AnalyzeContentCommandV1
from clipador_worker.storage import LocalMediaValidator


def test_candidate_windows_respect_sentence_boundaries_and_duration() -> None:
    segments = [
        Segment(0, 8, "Eu descobri uma coisa importante."),
        Segment(8, 17, "Ela mudou completamente meu trabalho."),
        Segment(17, 28, "Mas havia um problema difícil."),
        Segment(28, 39, "A solução apareceu depois de muitos testes."),
        Segment(39, 51, "Por isso este método funciona melhor."),
        Segment(51, 62, "Agora você pode aplicar os mesmos passos."),
    ]

    windows = candidate_windows(segments, 20, 40, 55)

    assert windows
    assert all(20 <= segments[end].end - segments[start].start <= 55 for start, end in windows)
    assert all(not analysis_module.continuation_start(segments[start].text) for start, _ in windows)


def test_scoring_rewards_a_strong_self_contained_hook() -> None:
    command = command_for(uuid4(), uuid4())
    segments = [
        Segment(0, 10, "Eu perdi tudo em três meses."),
        Segment(10, 24, "Então descobri o erro que ninguém percebe."),
        Segment(24, 42, "Por isso mudei o processo e consegui recuperar o negócio."),
    ]
    timeline = Timeline(.5, tuple(.8 for _ in range(100)))

    result = score_window(segments, 0, 2, timeline, timeline, command)

    assert result["hookScore"] >= .6
    assert result["narrativeScore"] >= .6
    assert result["category"] in {"STORY", "INSIGHT", "REVELATION"}
    assert 0 <= result["finalScore"] <= 1


def test_analyzer_generates_atomic_versioned_artifact(tmp_path: Path, monkeypatch) -> None:
    job_id, video_id = uuid4(), uuid4()
    video = tmp_path / "videos" / str(video_id) / "original.mp4"
    video.parent.mkdir(parents=True)
    video.write_bytes(b"video")
    audio = tmp_path / "jobs" / str(job_id) / "audio" / "normalized.wav"
    audio.parent.mkdir(parents=True)
    write_wave(audio, 70)
    transcript = tmp_path / "jobs" / str(job_id) / "transcript" / "transcript.json"
    transcript.parent.mkdir(parents=True)
    segments = [
        {"index": index, "start": index * 10, "end": (index + 1) * 10,
         "text": text, "confidence": .9, "words": []}
        for index, text in enumerate([
            "Eu descobri um segredo importante.", "Ele mudou a forma de trabalhar.",
            "Primeiro havia um problema real.", "Depois testamos três soluções diferentes.",
            "A melhor solução foi surpreendente.", "Por isso o resultado ficou muito melhor.",
            "Agora você pode aplicar a mesma ideia.",
        ])
    ]
    transcript.write_text(json.dumps({"schemaVersion": 1, "jobId": str(job_id),
                                      "videoId": str(video_id), "segments": segments}), encoding="utf-8")
    monkeypatch.setattr(analysis_module, "visual_timeline",
                        lambda _path, _settings: Timeline(1, tuple(.6 for _ in range(70))))
    settings = Settings("rabbit", 5672, "user", "password", tmp_path,
                        tmp_path / "inbox.sqlite3", 5, 30000, 30)
    command = command_for(job_id, video_id)

    details = LocalMultimodalClipAnalyzer(settings, JobArtifactStorage(tmp_path),
                                           LocalMediaValidator(tmp_path)).analyze(command)

    artifact = json.loads((tmp_path / command.analysis_storage_key).read_text(encoding="utf-8"))
    assert artifact["schemaVersion"] == 1
    assert artifact["provider"] == "local-multimodal-heuristic-v1"
    assert artifact["candidates"]
    assert details["candidateCount"] == len(artifact["candidates"])


def test_ollama_provider_validates_structured_scores(tmp_path: Path) -> None:
    settings = Settings("rabbit", 5672, "user", "password", tmp_path,
                        tmp_path / "inbox.sqlite3", 5, 30000, 30,
                        analysis_provider="ollama")
    provider = OllamaClipAnalysisProvider(settings, JobArtifactStorage(tmp_path),
                                          LocalMediaValidator(tmp_path))
    candidate = {
        "candidateKey": "candidate-1", "start": 0, "end": 45,
        "semanticScore": .4, "audioScore": .6, "visualScore": .5,
        "narrativeScore": .4, "hookScore": .4, "contextPenalty": .3,
        "finalScore": .4, "reason": "local", "hook": "local",
        "category": "OTHER", "sourceText": "Eu perdi tudo e descobri como recomeçar.",
    }
    response = {"candidates": [{
        "candidateKey": "candidate-1", "semanticScore": .95, "narrativeScore": .9,
        "hookScore": .9, "contextPenalty": .05, "reason": "História completa com revelação.",
        "hook": "Eu perdi tudo...", "category": "STORY",
    }]}
    provider._request = lambda _payload: {"message": {"content": json.dumps(response)}}

    enriched = provider._enrich([candidate], command_for(uuid4(), uuid4()))

    assert enriched[0]["semanticScore"] == .95
    assert enriched[0]["category"] == "STORY"
    assert enriched[0]["finalScore"] > candidate["finalScore"]


def command_for(job_id, video_id) -> AnalyzeContentCommandV1:
    return AnalyzeContentCommandV1(
        schemaVersion=1, messageId=uuid4(), taskType="ANALYZE_CONTENT", jobId=job_id,
        videoId=video_id, correlationId="correlation",
        videoStorageKey=f"videos/{video_id}/original.mp4",
        audioStorageKey=f"jobs/{job_id}/audio/normalized.wav",
        transcriptStorageKey=f"jobs/{job_id}/transcript/transcript.json",
        analysisStorageKey=f"jobs/{job_id}/analysis/candidates.json",
        minDurationSeconds=20, idealDurationSeconds=45, maxDurationSeconds=90,
        maxCandidates=100, semanticWeight=.30, audioWeight=.12, visualWeight=.08,
        narrativeWeight=.22, hookWeight=.23, contextPenaltyWeight=.15,
        attempt=1, createdAt=datetime.now(timezone.utc),
    )


def write_wave(path: Path, seconds: int) -> None:
    rate = 16000
    one_second = bytearray()
    for index in range(rate):
        value = int(math.sin(index / rate * math.pi * 440) * 5000)
        one_second.extend(value.to_bytes(2, "little", signed=True))
    with wave.open(str(path), "wb") as target:
        target.setnchannels(1)
        target.setsampwidth(2)
        target.setframerate(rate)
        target.writeframes(bytes(one_second) * seconds)
