import json
from datetime import datetime, timezone
from pathlib import Path
from types import SimpleNamespace
from uuid import uuid4

from clipador_worker.artifacts import JobArtifactStorage
from clipador_worker.config import Settings
from clipador_worker.contracts import TranscribeAudioCommandV1
from clipador_worker.transcription import FasterWhisperTranscriber


class FakeModel:
    def __init__(self) -> None:
        self.arguments = None

    def transcribe(self, _audio: str, **arguments):
        self.arguments = arguments
        word = SimpleNamespace(start=0.1, end=0.5, word=" Olá", probability=0.94)
        segment = SimpleNamespace(start=0.0, end=1.2, text=" Olá mundo ",
                                  avg_logprob=-0.1, words=[word])
        info = SimpleNamespace(language="pt", language_probability=0.98,
                               duration=2.0, duration_after_vad=1.2)
        return iter([segment]), info


def test_generates_versioned_transcript_with_vad_and_word_timestamps(tmp_path: Path) -> None:
    job_id, video_id = uuid4(), uuid4()
    audio = tmp_path / "jobs" / str(job_id) / "audio" / "normalized.wav"
    audio.parent.mkdir(parents=True)
    audio.write_bytes(b"RIFF-audio")
    settings = Settings("rabbit", 5672, "user", "password", tmp_path,
                        tmp_path / "inbox.sqlite3", 5, 30000, 30)
    transcriber = FasterWhisperTranscriber(settings, JobArtifactStorage(tmp_path))
    fake = FakeModel()
    transcriber._model = fake
    command = TranscribeAudioCommandV1(
        schemaVersion=1, messageId=uuid4(), taskType="TRANSCRIBE_AUDIO", jobId=job_id,
        videoId=video_id, correlationId="correlation",
        audioStorageKey=f"jobs/{job_id}/audio/normalized.wav",
        transcriptStorageKey=f"jobs/{job_id}/transcript/transcript.json",
        language=None, wordTimestamps=True, vadEnabled=True, attempt=1,
        createdAt=datetime.now(timezone.utc),
    )

    details = transcriber.transcribe(command)

    artifact = json.loads((tmp_path / command.transcript_storage_key).read_text(encoding="utf-8"))
    assert artifact["schemaVersion"] == 1
    assert artifact["detectedLanguage"] == "pt"
    assert artifact["segments"][0]["words"][0]["word"] == "Olá"
    assert fake.arguments["vad_filter"] is True
    assert fake.arguments["word_timestamps"] is True
    assert details["segmentCount"] == 1
