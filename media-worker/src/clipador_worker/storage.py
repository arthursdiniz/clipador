from __future__ import annotations

from pathlib import Path, PurePosixPath
from uuid import UUID


class InvalidStorageKey(ValueError):
    pass


class MediaTemporarilyUnavailable(RuntimeError):
    pass


class LocalMediaValidator:
    def __init__(self, root: Path) -> None:
        self._root = root.resolve()

    def validate(self, storage_key: str, video_id: UUID) -> dict[str, object]:
        resolved = self.resolve_video(storage_key, video_id)
        size = resolved.stat().st_size
        with resolved.open("rb") as stream:
            readable_bytes = len(stream.read(min(size, 4096)))
        if readable_bytes == 0:
            raise MediaTemporarilyUnavailable("Original media could not be read")
        return {"storageKey": storage_key, "sizeBytes": size, "readable": True}

    def resolve_video(self, storage_key: str, video_id: UUID) -> Path:
        key = PurePosixPath(storage_key)
        if key.is_absolute() or "\\" in storage_key or ".." in key.parts:
            raise InvalidStorageKey("Storage key is not a safe relative POSIX path")
        if len(key.parts) < 3 or key.parts[0] != "videos" or key.parts[1] != str(video_id):
            raise InvalidStorageKey("Storage key does not belong to the command video")

        candidate = self._root.joinpath(*key.parts)
        current = self._root
        for part in key.parts:
            current = current / part
            if current.is_symlink():
                raise InvalidStorageKey("Storage key must not traverse symbolic links")
        try:
            resolved = candidate.resolve(strict=True)
        except FileNotFoundError as exc:
            raise MediaTemporarilyUnavailable("Original media is not available in shared storage") from exc
        if not resolved.is_relative_to(self._root):
            raise InvalidStorageKey("Storage key resolves outside the configured root")
        if not resolved.is_file():
            raise InvalidStorageKey("Storage object must be a regular file")
        size = resolved.stat().st_size
        if size <= 0:
            raise MediaTemporarilyUnavailable("Original media is empty")
        return resolved
