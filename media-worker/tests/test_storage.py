from pathlib import Path
from uuid import uuid4

import pytest

from clipador_worker.storage import InvalidStorageKey, LocalMediaValidator, MediaTemporarilyUnavailable


def test_validates_readable_object_inside_video_namespace(tmp_path: Path) -> None:
    video_id = uuid4()
    media = tmp_path / "videos" / str(video_id) / "original.mp4"
    media.parent.mkdir(parents=True)
    media.write_bytes(b"media-bytes")

    details = LocalMediaValidator(tmp_path).validate(
        f"videos/{video_id}/original.mp4", video_id
    )

    assert details["sizeBytes"] == 11
    assert details["readable"] is True


@pytest.mark.parametrize("key", ["../secret.mp4", "/etc/passwd", "videos\\id\\original.mp4"])
def test_rejects_path_traversal_and_non_posix_keys(tmp_path: Path, key: str) -> None:
    with pytest.raises(InvalidStorageKey):
        LocalMediaValidator(tmp_path).validate(key, uuid4())


def test_missing_media_is_retryable(tmp_path: Path) -> None:
    video_id = uuid4()
    with pytest.raises(MediaTemporarilyUnavailable):
        LocalMediaValidator(tmp_path).validate(f"videos/{video_id}/original.mp4", video_id)
