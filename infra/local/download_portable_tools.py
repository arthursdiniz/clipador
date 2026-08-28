from __future__ import annotations

from dataclasses import dataclass
import hashlib
from pathlib import Path
import ssl
import urllib.request
import zipfile

import certifi


PROJECT_ROOT = Path(__file__).resolve().parents[2]
TOOLS_ROOT = PROJECT_ROOT / "tools"


@dataclass(frozen=True)
class Artifact:
    name: str
    url: str
    sha256: str
    archive: bool


ARTIFACTS = (
    Artifact(
        "yt-dlp",
        "https://github.com/yt-dlp/yt-dlp/releases/download/2026.08.19/yt-dlp.exe",
        "66674953fe251b89f4d08c5f0e35e0728679bd67ab3d7d05c0562af101dd3e7a",
        False,
    ),
    Artifact(
        "erlang",
        "https://github.com/erlang/otp/releases/download/OTP-27.3.4.16/otp_win64_27.3.4.16.zip",
        "a329f89ccad6921136e86f24ce2a6c99597c306d66ccec3d99d22c5ae007300c",
        True,
    ),
    Artifact(
        "rabbitmq",
        "https://github.com/rabbitmq/rabbitmq-server/releases/download/v4.3.5/"
        "rabbitmq-server-windows-4.3.5.zip",
        "462e626e276f1d670c0c45e96b36298e20c13d3efdd0f3f35e879e9868b4c9ab",
        True,
    ),
)


def main() -> None:
    TOOLS_ROOT.mkdir(parents=True, exist_ok=True)
    context = ssl.create_default_context(cafile=certifi.where())
    for artifact in ARTIFACTS:
        target = TOOLS_ROOT / artifact.name
        if installed(artifact, target):
            print(f"OK {artifact.name}: already installed")
            continue
        download_path = TOOLS_ROOT / f".{artifact.name}.download"
        download(artifact.url, download_path, context)
        verify(download_path, artifact.sha256)
        if artifact.archive:
            target.mkdir(parents=True, exist_ok=True)
            extract_safely(download_path, target)
            download_path.unlink()
        else:
            target.mkdir(parents=True, exist_ok=True)
            download_path.replace(target / "yt-dlp.exe")
        (target / ".clipador-version").write_text(artifact.sha256 + "\n", encoding="ascii")
        print(f"OK {artifact.name}: installed and verified")


def installed(artifact: Artifact, target: Path) -> bool:
    marker = target / ".clipador-version"
    expected_executable = {
        "yt-dlp": "yt-dlp.exe",
        "erlang": "erl.exe",
        "rabbitmq": "rabbitmqctl.bat",
    }[artifact.name]
    return (marker.is_file() and marker.read_text(encoding="ascii").strip() == artifact.sha256
            and any(target.rglob(expected_executable)))


def download(url: str, target: Path, context: ssl.SSLContext) -> None:
    request = urllib.request.Request(url, headers={"User-Agent": "Clipador-local-setup/1"})
    with urllib.request.urlopen(request, context=context, timeout=180) as response, \
            target.open("wb") as output:
        while chunk := response.read(1024 * 1024):
            output.write(chunk)


def verify(path: Path, expected: str) -> None:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        while chunk := stream.read(1024 * 1024):
            digest.update(chunk)
    if digest.hexdigest() != expected:
        path.unlink(missing_ok=True)
        raise RuntimeError(f"SHA-256 mismatch for {path.name}")


def extract_safely(archive: Path, target: Path) -> None:
    resolved_target = target.resolve()
    with zipfile.ZipFile(archive) as bundle:
        for member in bundle.infolist():
            destination = (target / member.filename).resolve()
            if not destination.is_relative_to(resolved_target):
                raise RuntimeError(f"Unsafe archive member: {member.filename}")
        bundle.extractall(target)


if __name__ == "__main__":
    main()
