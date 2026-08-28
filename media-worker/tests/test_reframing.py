from datetime import datetime, timezone
from pathlib import Path
from uuid import uuid4

import numpy as np

import clipador_worker.reframing as reframing_module
from clipador_worker.contracts import RenderClipsCommandV2
from clipador_worker.reframing import (CropKeyframe, FaceBox, SmartReframePlanner,
                                       compress_keyframes, crop_dimensions, limit_pan,
                                       subject_center)
from clipador_worker.rendering import keyframe_expression


def test_crop_dimensions_preserve_target_aspect_and_even_values() -> None:
    assert crop_dimensions(1920, 1080, 9 / 16) == (606, 1080)
    assert crop_dimensions(1080, 1920, 16 / 9) == (1080, 606)
    assert crop_dimensions(1080, 1080, 1) == (1080, 1080)


def test_auto_groups_people_only_when_they_fit_the_crop() -> None:
    close_faces = [FaceBox(500, 200, 120, 120), FaceBox(700, 210, 120, 120)]
    far_faces = [FaceBox(50, 200, 120, 120), FaceBox(1700, 210, 120, 120)]

    grouped_center, grouped = subject_center(
        close_faces, (960, 540), 700, 1080, 1920, 1080, "AUTO")
    focused_center, far_grouped = subject_center(
        far_faces, (1800, 260), 606, 1080, 1920, 1080, "AUTO")

    assert grouped is True
    assert 600 < grouped_center[0] < 720
    assert far_grouped is False
    assert focused_center[0] > 1700


def test_pan_is_bounded_and_keyframes_are_compressed() -> None:
    bounded = limit_pan((200, 200), (1000, 800), .5, .2, 1000, 800)
    frames = [CropKeyframe(float(index), index * 20, 0) for index in range(20)]

    compressed = compress_keyframes(frames, 400, 800, 6)

    assert bounded == (300, 280)
    assert len(compressed) == 6
    assert compressed[0] == frames[0]
    assert compressed[-1] == frames[-1]


def test_keyframe_expression_interpolates_without_shell_input() -> None:
    expression = keyframe_expression(
        (CropKeyframe(0, 10, 0), CropKeyframe(1, 30, 0), CropKeyframe(2, 50, 0)), "x")

    assert "if(lt(t\\,1.000)" in expression
    assert "10+(20)*(t-0.000)/1.000" in expression
    assert ";" not in expression


def test_planner_tracks_faces_with_even_bounded_coordinates(monkeypatch) -> None:
    frames = [np.zeros((360, 640, 3), dtype=np.uint8) for _ in range(4)]

    class FakeCapture:
        def __init__(self, _source: str) -> None:
            self.index = 0

        def isOpened(self) -> bool:
            return True

        def get(self, property_id: int) -> float:
            return 640 if property_id == reframing_module.cv2.CAP_PROP_FRAME_WIDTH else 360

        def set(self, _property_id: int, _value: float) -> bool:
            return True

        def read(self):
            frame = frames[min(self.index, len(frames) - 1)]
            self.index += 1
            return True, frame

        def release(self) -> None:
            pass

    class MovingFaceDetector:
        def __init__(self) -> None:
            self.index = 0

        def detect(self, _frame, _minimum_size: int) -> list[FaceBox]:
            x = [40, 180, 330, 460][min(self.index, 3)]
            self.index += 1
            return [FaceBox(x, 80, 80, 80)]

    monkeypatch.setattr(reframing_module.cv2, "VideoCapture", FakeCapture)
    command = smart_command()
    plan = SmartReframePlanner(MovingFaceDetector()).plan(
        Path("synthetic.mp4"), command.candidates[0], command.formats[0], command)

    assert plan.strategy == "FACE_FOCUS"
    assert plan.face_detection_coverage == 1
    assert len(plan.keyframes) >= 2
    assert all(frame.x % 2 == 0 and 0 <= frame.x <= 438 for frame in plan.keyframes)
    assert all(frame.y == 0 for frame in plan.keyframes)


def smart_command() -> RenderClipsCommandV2:
    job_id, video_id = uuid4(), uuid4()
    return RenderClipsCommandV2.model_validate({
        "schemaVersion": 2, "messageId": str(uuid4()), "taskType": "RENDER_CLIPS",
        "jobId": str(job_id), "videoId": str(video_id), "correlationId": "correlation",
        "videoStorageKey": f"videos/{video_id}/original.mp4",
        "transcriptStorageKey": f"jobs/{job_id}/transcript/transcript.json",
        "manifestStorageKey": f"jobs/{job_id}/render/manifest.json",
        "candidates": [{"candidateId": str(uuid4()), "start": 0, "end": 3}],
        "formats": [{"format": "VERTICAL_9_16", "width": 360, "height": 640}],
        "burnInSubtitles": True, "videoCrf": 21, "encoderPreset": "medium",
        "audioBitrateKbps": 160, "outputFps": 30,
        "smartReframingEnabled": True, "reframingMode": "AUTO",
        "reframingSampleFps": 1, "reframingSmoothing": .5,
        "reframingMaxPanRatioPerSecond": .2, "reframingFaceMinSizeRatio": .025,
        "reframingDetectionWidth": 640, "reframingMaxKeyframes": 16,
        "attempt": 1, "createdAt": datetime.now(timezone.utc).isoformat(),
    })
