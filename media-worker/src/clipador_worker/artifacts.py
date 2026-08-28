from __future__ import annotations

import os
from pathlib import Path, PurePosixPath
from uuid import UUID

from .storage import InvalidStorageKey, MediaTemporarilyUnavailable


class JobArtifactStorage:
    def __init__(self, root: Path) -> None:
        self._root = root.resolve()

    def resolve_input(self, key_value: str, job_id: UUID) -> Path:
        key = self._job_key(key_value, job_id)
        candidate = self._root.joinpath(*key.parts)
        self._reject_symlinks(candidate)
        try:
            resolved = candidate.resolve(strict=True)
        except FileNotFoundError as exc:
            raise MediaTemporarilyUnavailable(f"Input artifact is unavailable: {key_value}") from exc
        if not resolved.is_relative_to(self._root) or not resolved.is_file():
            raise InvalidStorageKey("Input artifact is not a regular managed file")
        if resolved.stat().st_size <= 0:
            raise MediaTemporarilyUnavailable("Input artifact is empty")
        return resolved

    def output_target(self, key_value: str, job_id: UUID) -> Path:
        key = self._job_key(key_value, job_id)
        target = self._root.joinpath(*key.parts)
        target.parent.mkdir(parents=True, exist_ok=True)
        self._reject_symlinks(target)
        parent = target.parent.resolve(strict=True)
        if not parent.is_relative_to(self._root):
            raise InvalidStorageKey("Output artifact resolves outside storage")
        return target

    def temporary(self, target: Path, message_id: UUID) -> Path:
        return target.with_name(f".{target.name}.part-{message_id}")

    def commit(self, temporary: Path, target: Path) -> None:
        os.replace(temporary, target)

    def _job_key(self, key_value: str, job_id: UUID) -> PurePosixPath:
        key = PurePosixPath(key_value)
        if key.is_absolute() or "\\" in key_value or ".." in key.parts:
            raise InvalidStorageKey("Artifact key is not a safe relative POSIX path")
        if len(key.parts) < 3 or key.parts[0] != "jobs" or key.parts[1] != str(job_id):
            raise InvalidStorageKey("Artifact key does not belong to the command job")
        return key

    def _reject_symlinks(self, candidate: Path) -> None:
        current = self._root
        relative = candidate.relative_to(self._root)
        for part in relative.parts:
            current = current / part
            if current.is_symlink():
                raise InvalidStorageKey("Artifact path must not traverse symbolic links")
