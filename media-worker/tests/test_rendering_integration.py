from datetime import datetime, timezone
import json
import os
from pathlib import Path
import subprocess
from uuid import uuid4

import pytest

from clipador_worker.artifacts import JobArtifactStorage
from clipador_worker.config import Settings
from clipador_worker.contracts import RenderClipsCommandV2
from clipador_worker.rendering import FfmpegClipRenderer
from clipador_worker.storage import LocalMediaValidator


FFMPEG = os.getenv("CLIPADOR_TEST_FFMPEG")
FFPROBE = os.getenv("CLIPADOR_TEST_FFPROBE")


@pytest.mark.skipif(not FFMPEG or not FFPROBE,
                    reason="Set CLIPADOR_TEST_FFMPEG and CLIPADOR_TEST_FFPROBE")
def test_real_ffmpeg_render_with_smart_crop_burn_in_and_thumbnail(tmp_path: Path) -> None:
    job_id, video_id, candidate_id = uuid4(), uuid4(), uuid4()
    video_key = f"videos/{video_id}/original.mp4"
    source = tmp_path / video_key
    source.parent.mkdir(parents=True)
    generated = subprocess.run([
        str(FFMPEG), "-nostdin", "-hide_banner", "-loglevel", "error", "-y",
        "-f", "lavfi", "-i", "testsrc2=size=640x360:rate=30",
        "-f", "lavfi", "-i", "sine=frequency=440:sample_rate=48000",
        "-t", "3", "-c:v", "libx264", "-pix_fmt", "yuv420p", "-c:a", "aac",
        "-shortest", str(source),
    ], capture_output=True, text=True, check=False, timeout=60)
    assert generated.returncode == 0, generated.stderr

    transcript_key = f"jobs/{job_id}/transcript/transcript.json"
    transcript = tmp_path / transcript_key
    transcript.parent.mkdir(parents=True)
    transcript.write_text(json.dumps({
        "schemaVersion": 1, "jobId": str(job_id), "videoId": str(video_id),
        "segments": [{
            "start": 0, "end": 3, "text": "Render real validado com legenda.",
            "words": [
                {"start": .1, "end": .6, "word": "Render"},
                {"start": .6, "end": 1.1, "word": "real"},
                {"start": 1.1, "end": 1.7, "word": "validado"},
                {"start": 1.7, "end": 2, "word": "com"},
                {"start": 2, "end": 2.6, "word": "legenda."},
            ],
        }],
    }), encoding="utf-8")
    command = RenderClipsCommandV2.model_validate({
        "schemaVersion": 2, "messageId": str(uuid4()), "taskType": "RENDER_CLIPS",
        "jobId": str(job_id), "videoId": str(video_id), "correlationId": "real-render",
        "videoStorageKey": video_key, "transcriptStorageKey": transcript_key,
        "manifestStorageKey": f"jobs/{job_id}/render/manifest.json",
        "candidates": [{"candidateId": str(candidate_id), "start": 0, "end": 3}],
        "formats": [{"format": "VERTICAL_9_16", "width": 360, "height": 640}],
        "burnInSubtitles": True, "videoCrf": 23, "encoderPreset": "veryfast",
        "audioBitrateKbps": 128, "outputFps": 30,
        "smartReframingEnabled": True, "reframingMode": "AUTO",
        "reframingSampleFps": 1, "reframingSmoothing": .82,
        "reframingMaxPanRatioPerSecond": .35, "reframingFaceMinSizeRatio": .025,
        "reframingDetectionWidth": 640, "reframingMaxKeyframes": 16,
        "attempt": 1, "createdAt": datetime.now(timezone.utc).isoformat(),
    })
    settings = Settings("rabbit", 5672, "user", "password", tmp_path,
                        tmp_path / "inbox.sqlite3", 1, 1000, 5,
                        ffmpeg_executable=str(FFMPEG))

    result = FfmpegClipRenderer(
        settings, JobArtifactStorage(tmp_path), LocalMediaValidator(tmp_path)).render(command)

    assert result["succeededCount"] == 1
    manifest = json.loads((tmp_path / command.manifest_storage_key).read_text(encoding="utf-8"))
    rendered = manifest["renders"][0]
    assert rendered["status"] == "SUCCEEDED"
    assert rendered["reframing"]["strategy"] in {"MOTION_FOCUS", "CENTER_FALLBACK"}
    assert (tmp_path / rendered["thumbnailStorageKey"]).stat().st_size > 100
    probe = subprocess.run([
        str(FFPROBE), "-v", "error", "-select_streams", "v:0",
        "-show_entries", "stream=width,height,codec_name", "-of", "json",
        str(tmp_path / rendered["storageKey"]),
    ], capture_output=True, text=True, check=False, timeout=30)
    assert probe.returncode == 0, probe.stderr
    stream = json.loads(probe.stdout)["streams"][0]
    assert stream == {"codec_name": "h264", "width": 360, "height": 640}
