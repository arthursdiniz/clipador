from __future__ import annotations

import argparse
import base64
import concurrent.futures
import statistics
import time
import urllib.error
import urllib.request
from pathlib import Path


def load_env(path: Path) -> dict[str, str]:
    values: dict[str, str] = {}
    for raw_line in path.read_text(encoding="utf-8-sig").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        name, value = line.split("=", 1)
        values[name.strip()] = value.strip()
    return values


def request_once(url: str, authorization: str, timeout: float) -> tuple[bool, float, int]:
    request = urllib.request.Request(url, headers={"Authorization": authorization,
                                                   "Accept": "application/json"})
    started = time.perf_counter()
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            response.read(1024)
            status = response.status
    except urllib.error.HTTPError as error:
        status = error.code
    except OSError:
        status = 0
    elapsed_ms = (time.perf_counter() - started) * 1000
    return 200 <= status < 300, elapsed_ms, status


def main() -> int:
    parser = argparse.ArgumentParser(description="Read-only concurrent smoke test for the Clipador API")
    parser.add_argument("--base-url", default="http://127.0.0.1:8080")
    parser.add_argument("--requests", type=int, default=100)
    parser.add_argument("--concurrency", type=int, default=8)
    parser.add_argument("--timeout-seconds", type=float, default=10)
    parser.add_argument("--max-p95-ms", type=float, default=2000)
    args = parser.parse_args()
    if not 1 <= args.requests <= 10_000 or not 1 <= args.concurrency <= 256:
        parser.error("requests or concurrency is outside the safe test range")

    project_root = Path(__file__).resolve().parents[2]
    env = load_env(project_root / ".env.local")
    username = env.get("CLIPADOR_SECURITY_USERNAME", "")
    password = env.get("CLIPADOR_SECURITY_PASSWORD", "")
    if not username or not password:
        raise SystemExit("Missing local API credentials in .env.local")
    token = base64.b64encode(f"{username}:{password}".encode()).decode()
    authorization = f"Basic {token}"
    url = args.base_url.rstrip("/") + "/api/v1/videos?size=1"

    with concurrent.futures.ThreadPoolExecutor(max_workers=args.concurrency) as executor:
        results = list(executor.map(
            lambda _: request_once(url, authorization, args.timeout_seconds),
            range(args.requests),
        ))
    latencies = sorted(result[1] for result in results)
    failures = [result for result in results if not result[0]]
    p95_index = max(0, min(len(latencies) - 1, int(len(latencies) * 0.95) - 1))
    p95 = latencies[p95_index]
    mean = statistics.fmean(latencies)
    print(f"requests={len(results)} failures={len(failures)} mean_ms={mean:.1f} p95_ms={p95:.1f}")
    if failures:
        statuses = sorted({result[2] for result in failures})
        print(f"failure_statuses={statuses}")
        return 1
    if p95 > args.max_p95_ms:
        print(f"p95 exceeded limit {args.max_p95_ms:.1f}ms")
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
