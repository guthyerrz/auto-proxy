#!/usr/bin/env python3
"""Verify that the expected Singular config request was captured by mitmproxy."""

import json
import sys
from pathlib import Path

CAPTURE_FILE = Path("/tmp/mitmproxy_captured.har")

REQUIRED_FRAGMENTS = [
    "sdk-api-v1.singular.net",
    "/api/v1/config",
    "a=wildlife_dev_127ec072",
    "i=com.fungames.blockcraft",
]


def main() -> int:
    if not CAPTURE_FILE.exists():
        print(f"ERROR: Capture file not found: {CAPTURE_FILE}")
        return 1

    with open(CAPTURE_FILE) as f:
        har = json.load(f)

    entries = har.get("log", {}).get("entries", [])

    print(f"=== {len(entries)} captured requests ===")
    for entry in entries:
        req = entry.get("request", {})
        print(f"  {req.get('method')} {req.get('url')}")
    print("=============================")

    matches = [
        entry
        for entry in entries
        if entry.get("request", {}).get("method") == "POST"
        and all(
            frag in entry.get("request", {}).get("url", "")
            for frag in REQUIRED_FRAGMENTS
        )
    ]

    if matches:
        print(f"SUCCESS: Found {len(matches)} matching Singular config request(s)")
        return 0

    print("FAILURE: No matching Singular config request found")
    return 1


if __name__ == "__main__":
    sys.exit(main())
