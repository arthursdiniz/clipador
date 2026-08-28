from pathlib import Path
from uuid import uuid4

from clipador_worker.inbox import InboxStore


def test_persists_completed_result_across_restarts(tmp_path: Path) -> None:
    path = tmp_path / "inbox.sqlite3"
    message_id = uuid4()
    first = InboxStore(path)
    first.complete(message_id, b'{"status":"SUCCEEDED"}')
    first.close()

    reopened = InboxStore(path)
    assert reopened.result_for(message_id) == b'{"status":"SUCCEEDED"}'
    reopened.close()


def test_first_result_wins_for_duplicate_command(tmp_path: Path) -> None:
    inbox = InboxStore(tmp_path / "inbox.sqlite3")
    message_id = uuid4()
    inbox.complete(message_id, b"first")
    inbox.complete(message_id, b"second")

    assert inbox.result_for(message_id) == b"first"
    inbox.close()
