#!/usr/bin/env python3
"""Apply the minimal Storm Runtime branding patch to the pinned Winlator app source.

The upstream runtime implementation is retained; only Android package identity and
user-visible app name/version are changed here. The script is idempotent and fails
closed if upstream changes the expected source shape.
"""
from pathlib import Path
import sys

root = Path(sys.argv[1]).resolve()
gradle = root / "app/build.gradle"
strings = root / "app/src/main/res/values/strings.xml"

if not gradle.is_file() or not strings.is_file():
    raise SystemExit("Pinned Winlator Android app source is incomplete.")

g = gradle.read_text(encoding="utf-8")
if "applicationId 'id.stormruntime.naruto'" not in g:
    old = "applicationId 'com.winlator'"
    if g.count(old) != 1:
        raise SystemExit("Cannot safely identify the upstream applicationId declaration.")
    g = g.replace(old, "applicationId 'id.stormruntime.naruto'")
if "versionName \"0.2.0-storm\"" not in g:
    import re
    g, count = re.subn(r'versionName\s+"[^"]+"', 'versionName "0.2.0-storm"', g, count=1)
    if count != 1:
        raise SystemExit("Cannot safely update upstream app versionName.")
gradle.write_text(g, encoding="utf-8")

s = strings.read_text(encoding="utf-8")
old_name = '<string name="app_name">Winlator</string>'
new_name = '<string name="app_name">Storm Runtime Naruto</string>'
if new_name not in s:
    if s.count(old_name) != 1:
        raise SystemExit("Cannot safely identify upstream app_name string.")
    s = s.replace(old_name, new_name)
strings.write_text(s, encoding="utf-8")

print("Applied Storm Runtime package identity and app label.")
