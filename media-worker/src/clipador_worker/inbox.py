from __future__ import annotations

import sqlite3
import threading
from datetime import datetime, timezone
from pathlib import Path
from uuid import UUID


class InboxStore:
    def __init__(self, path: Path) -> None:
        path.parent.mkdir(parents=True, exist_ok=True)
        self._connection = sqlite3.connect(path, check_same_thread=False)
        self._lock = threading.Lock()
        with self._connection:
            self._connection.execute(
                """CREATE TABLE IF NOT EXISTS processed_message (
                    message_id TEXT PRIMARY KEY,
                    result_json BLOB NOT NULL,
                    processed_at TEXT NOT NULL
                )"""
            )

    def result_for(self, message_id: UUID) -> bytes | None:
        with self._lock:
            row = self._connection.execute(
                "SELECT result_json FROM processed_message WHERE message_id = ?", (str(message_id),)
            ).fetchone()
        return bytes(row[0]) if row else None

    def complete(self, message_id: UUID, result_json: bytes) -> None:
        with self._lock, self._connection:
            self._connection.execute(
                "INSERT OR IGNORE INTO processed_message(message_id, result_json, processed_at) VALUES (?, ?, ?)",
                (str(message_id), result_json, datetime.now(timezone.utc).isoformat()),
            )

    def close(self) -> None:
        with self._lock:
            self._connection.close()
