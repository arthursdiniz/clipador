from __future__ import annotations

from dataclasses import dataclass
import math
from pathlib import Path
from typing import Any, Protocol

import cv2

from .contracts import RenderCandidateSpec, RenderClipsCommandV2, RenderFormatSpec


@dataclass(frozen=True)
class FaceBox:
    x: float
    y: float
    width: float
    height: float
    kind: str = "FACE"

    @property
    def center(self) -> tuple[float, float]:
        return self.x + self.width / 2, self.y + self.height / 2

    @property
    def area(self) -> float:
        return self.width * self.height


@dataclass(frozen=True)
class CropKeyframe:
    time: float
    x: int
    y: int


@dataclass(frozen=True)
class ReframePlan:
    strategy: str
    source_width: int
    source_height: int
    crop_width: int
    crop_height: int
    keyframes: tuple[CropKeyframe, ...]
    face_detection_coverage: float
    subject_detection_coverage: float
    fallback: bool

    def manifest(self) -> dict[str, object]:
        return {
            "strategy": self.strategy,
            "faceDetectionCoverage": round(self.face_detection_coverage, 4),
            "subjectDetectionCoverage": round(self.subject_detection_coverage, 4),
            "keyframeCount": len(self.keyframes),
            "fallback": self.fallback,
        }


class SubjectDetector(Protocol):
    def detect(self, frame: Any, minimum_size: int) -> list[FaceBox]: ...


class HaarSubjectDetector:
    def __init__(self) -> None:
        cascade_path = Path(cv2.data.haarcascades) / "haarcascade_frontalface_default.xml"
        self._cascade = cv2.CascadeClassifier(str(cascade_path))
        upper_body_path = Path(cv2.data.haarcascades) / "haarcascade_upperbody.xml"
        self._upper_body_cascade = cv2.CascadeClassifier(str(upper_body_path))
        if self._cascade.empty() or self._upper_body_cascade.empty():
            raise RuntimeError("OpenCV subject cascades could not be loaded")

    def detect(self, frame: Any, minimum_size: int) -> list[FaceBox]:
        gray = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY)
        gray = cv2.equalizeHist(gray)
        boxes = self._cascade.detectMultiScale(
            gray,
            scaleFactor=1.1,
            minNeighbors=5,
            minSize=(minimum_size, minimum_size),
            flags=cv2.CASCADE_SCALE_IMAGE,
        )
        if len(boxes):
            return [FaceBox(float(x), float(y), float(width), float(height), "FACE")
                    for x, y, width, height in boxes]
        bodies = self._upper_body_cascade.detectMultiScale(
            gray,
            scaleFactor=1.08,
            minNeighbors=4,
            minSize=(minimum_size, minimum_size),
            flags=cv2.CASCADE_SCALE_IMAGE,
        )
        return [FaceBox(float(x), float(y), float(width), float(height), "PERSON")
                for x, y, width, height in bodies]


class SmartReframePlanner:
    def __init__(self, detector: SubjectDetector | None = None) -> None:
        self._detector = detector or HaarSubjectDetector()

    def plan(self, source: Path, candidate: RenderCandidateSpec,
             format_spec: RenderFormatSpec, command: RenderClipsCommandV2) -> ReframePlan:
        capture = cv2.VideoCapture(str(source))
        try:
            if not capture.isOpened():
                raise ValueError("OpenCV could not open the source video")
            source_width = int(capture.get(cv2.CAP_PROP_FRAME_WIDTH))
            source_height = int(capture.get(cv2.CAP_PROP_FRAME_HEIGHT))
            if source_width < 2 or source_height < 2:
                raise ValueError("Source video dimensions are invalid")
            crop_width, crop_height = crop_dimensions(
                source_width, source_height, format_spec.width / format_spec.height)
            if crop_width == source_width and crop_height == source_height:
                return center_plan("NO_CROP", source_width, source_height,
                                   crop_width, crop_height, candidate.end - candidate.start, False)
            if command.reframing_mode == "BLURRED_BACKGROUND" or not command.smart_reframing_enabled:
                return center_plan("BLURRED_BACKGROUND", source_width, source_height,
                                   crop_width, crop_height, candidate.end - candidate.start, True)
            return self._analyze(capture, candidate, source_width, source_height,
                                 crop_width, crop_height, command)
        finally:
            capture.release()

    def _analyze(self, capture: Any, candidate: RenderCandidateSpec,
                 source_width: int, source_height: int, crop_width: int, crop_height: int,
                 command: RenderClipsCommandV2) -> ReframePlan:
        duration = candidate.end - candidate.start
        interval = 1 / command.reframing_sample_fps
        sample_count = max(2, math.floor(duration / interval) + 1)
        scale = min(1.0, command.reframing_detection_width / source_width)
        detection_width = max(2, round(source_width * scale))
        detection_height = max(2, round(source_height * scale))
        minimum_face = max(20, round(min(detection_width, detection_height)
                                     * math.sqrt(command.reframing_face_min_size_ratio)))
        previous_gray: Any | None = None
        previous_center = (source_width / 2, source_height / 2)
        previous_time = 0.0
        observations: list[CropKeyframe] = []
        face_frames = 0
        subject_frames = 0
        motion_frames = 0
        used_group = False

        for index in range(sample_count):
            relative_time = min(duration, index * interval)
            capture.set(cv2.CAP_PROP_POS_MSEC, (candidate.start + relative_time) * 1000)
            ok, frame = capture.read()
            if not ok:
                continue
            analysis_frame = cv2.resize(frame, (detection_width, detection_height),
                                        interpolation=cv2.INTER_AREA)
            faces = self._detector.detect(analysis_frame, minimum_face)
            faces = [scale_box(face, 1 / scale) for face in faces]
            gray = cv2.resize(cv2.cvtColor(analysis_frame, cv2.COLOR_BGR2GRAY), (160, 90),
                              interpolation=cv2.INTER_AREA)
            scene_cut = previous_gray is not None and cv2.mean(
                cv2.absdiff(gray, previous_gray))[0] >= 42
            motion = motion_center(previous_gray, gray, source_width, source_height)
            previous_gray = gray

            target, grouped = subject_center(faces, previous_center, crop_width, crop_height,
                                             source_width, source_height, command.reframing_mode)
            if faces:
                subject_frames += 1
                face_frames += int(any(face.kind == "FACE" for face in faces))
                used_group = used_group or grouped
                target = (target[0], target[1] + crop_height * .12)
            elif motion is not None:
                motion_frames += 1
                target = motion
            else:
                target = previous_center

            target = clamp_center(target, crop_width, crop_height, source_width, source_height)
            elapsed = max(interval, relative_time - previous_time)
            if scene_cut or not observations:
                smoothed = target
            else:
                retention = command.reframing_smoothing
                blended = (previous_center[0] * retention + target[0] * (1 - retention),
                           previous_center[1] * retention + target[1] * (1 - retention))
                smoothed = limit_pan(previous_center, blended, elapsed,
                                     command.reframing_max_pan_ratio_per_second,
                                     source_width, source_height)
            smoothed = clamp_center(smoothed, crop_width, crop_height, source_width, source_height)
            observations.append(CropKeyframe(round(relative_time, 3),
                                             even(round(smoothed[0] - crop_width / 2)),
                                             even(round(smoothed[1] - crop_height / 2))))
            previous_center = smoothed
            previous_time = relative_time

        if not observations:
            return center_plan("CENTER_FALLBACK", source_width, source_height,
                               crop_width, crop_height, duration, True)
        if observations[-1].time < duration:
            last = observations[-1]
            observations.append(CropKeyframe(round(duration, 3), last.x, last.y))
        keyframes = compress_keyframes(observations, crop_width, crop_height,
                                      command.reframing_max_keyframes)
        coverage = face_frames / max(1, len(observations))
        subject_coverage = subject_frames / max(1, len(observations))
        if face_frames:
            strategy = "FACE_GROUP" if used_group else "FACE_FOCUS"
        elif subject_frames:
            strategy = "PERSON_GROUP" if used_group else "PERSON_FOCUS"
        elif motion_frames:
            strategy = "MOTION_FOCUS"
        else:
            strategy = "CENTER_FALLBACK"
        return ReframePlan(strategy, source_width, source_height, crop_width, crop_height,
                           tuple(keyframes), coverage, subject_coverage,
                           not bool(subject_frames or motion_frames))


def crop_dimensions(source_width: int, source_height: int,
                    target_ratio: float) -> tuple[int, int]:
    source_ratio = source_width / source_height
    if source_ratio > target_ratio:
        return min(source_width, max(2, even(math.floor(source_height * target_ratio)))), max(2, even(source_height))
    if source_ratio < target_ratio:
        return max(2, even(source_width)), min(source_height, max(2, even(math.floor(source_width / target_ratio))))
    return max(2, even(source_width)), max(2, even(source_height))


def center_plan(strategy: str, source_width: int, source_height: int,
                crop_width: int, crop_height: int, duration: float, fallback: bool) -> ReframePlan:
    x = even(round((source_width - crop_width) / 2))
    y = even(round((source_height - crop_height) / 2))
    keyframes = (CropKeyframe(0, x, y), CropKeyframe(round(duration, 3), x, y))
    return ReframePlan(strategy, source_width, source_height, crop_width, crop_height,
                       keyframes, 0, 0, fallback)


def subject_center(faces: list[FaceBox], previous: tuple[float, float], crop_width: int,
                   crop_height: int, source_width: int, source_height: int,
                   mode: str) -> tuple[tuple[float, float], bool]:
    if not faces:
        return previous, False
    union = union_box(faces)
    group_fits = union.width <= crop_width * .82 and union.height <= crop_height * .72
    if len(faces) > 1 and mode in {"AUTO", "GROUP"} and (group_fits or mode == "GROUP"):
        return union.center, True
    diagonal = math.hypot(source_width, source_height)
    selected = max(faces, key=lambda face: (
        face.area / (source_width * source_height) * 4
        - math.dist(face.center, previous) / diagonal
        - math.dist(face.center, (source_width / 2, source_height / 2)) / diagonal * .2
    ))
    return selected.center, False


def union_box(boxes: list[FaceBox]) -> FaceBox:
    left = min(box.x for box in boxes)
    top = min(box.y for box in boxes)
    right = max(box.x + box.width for box in boxes)
    bottom = max(box.y + box.height for box in boxes)
    return FaceBox(left, top, right - left, bottom - top)


def scale_box(box: FaceBox, factor: float) -> FaceBox:
    return FaceBox(box.x * factor, box.y * factor, box.width * factor, box.height * factor, box.kind)


def motion_center(previous_gray: Any | None, gray: Any, source_width: int,
                  source_height: int) -> tuple[float, float] | None:
    if previous_gray is None:
        return None
    difference = cv2.absdiff(gray, previous_gray)
    _, mask = cv2.threshold(difference, 24, 255, cv2.THRESH_BINARY)
    mask = cv2.morphologyEx(mask, cv2.MORPH_OPEN, None, iterations=1)
    if cv2.countNonZero(mask) < mask.shape[0] * mask.shape[1] * .006:
        return None
    moments = cv2.moments(mask, binaryImage=True)
    if moments["m00"] <= 0:
        return None
    return (moments["m10"] / moments["m00"] / mask.shape[1] * source_width,
            moments["m01"] / moments["m00"] / mask.shape[0] * source_height)


def clamp_center(center: tuple[float, float], crop_width: int, crop_height: int,
                 source_width: int, source_height: int) -> tuple[float, float]:
    return (min(max(center[0], crop_width / 2), source_width - crop_width / 2),
            min(max(center[1], crop_height / 2), source_height - crop_height / 2))


def limit_pan(previous: tuple[float, float], target: tuple[float, float], elapsed: float,
              ratio_per_second: float, source_width: int,
              source_height: int) -> tuple[float, float]:
    maximum_x = source_width * ratio_per_second * elapsed
    maximum_y = source_height * ratio_per_second * elapsed
    return (previous[0] + max(-maximum_x, min(maximum_x, target[0] - previous[0])),
            previous[1] + max(-maximum_y, min(maximum_y, target[1] - previous[1])))


def compress_keyframes(frames: list[CropKeyframe], crop_width: int, crop_height: int,
                       maximum: int) -> list[CropKeyframe]:
    threshold = max(2, round(min(crop_width, crop_height) * .008))
    kept = [frames[0]]
    for frame in frames[1:-1]:
        previous = kept[-1]
        if abs(frame.x - previous.x) >= threshold or abs(frame.y - previous.y) >= threshold:
            kept.append(frame)
    if frames[-1] != kept[-1]:
        kept.append(frames[-1])
    if len(kept) <= maximum:
        return kept
    indexes = [round(index * (len(kept) - 1) / (maximum - 1)) for index in range(maximum)]
    return [kept[index] for index in dict.fromkeys(indexes)]


def even(value: int) -> int:
    return max(0, value - value % 2)
