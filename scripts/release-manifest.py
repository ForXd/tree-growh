#!/usr/bin/env python3
"""Generate a manifest from the actual signed APK, never from an unsigned build."""
import hashlib
import json
import pathlib
import re
import sys

version, code, filename = sys.argv[1:]
assert re.fullmatch(r"[0-9]{1,5}\.[0-9]{1,5}\.[0-9]{1,5}", version)
assert int(code) > 0
apk = pathlib.Path(filename)
assert apk.name == f"qingya-{version}.apk"
size = apk.stat().st_size
assert 0 < size <= 32 * 1024 * 1024
digest = hashlib.sha256(apk.read_bytes()).hexdigest()
notes = pathlib.Path("docs/release-notes.txt").read_text().strip()
manifest = dict(version=version, versionCode=int(code), url=f"https://github.com/ForXd/tree-growh/releases/download/v{version}/{apk.name}", bytes=size, sha256=digest, notes=notes)
(apk.parent / "update.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n")
(apk.parent / "SHA256SUMS").write_text(f"{digest}  {apk.name}\n")
