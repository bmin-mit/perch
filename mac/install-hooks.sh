#!/bin/bash
# Installs Perch.app into /Applications and registers `perch hook` with Claude Code
# (in ~/.claude/settings.json) so the phone can show which sessions are working or waiting.
set -euo pipefail
cd "$(dirname "$0")"

[ -d build/Perch.app ] || ./build.sh
rm -rf /Applications/Perch.app
cp -R build/Perch.app /Applications/Perch.app
HOOK="/Applications/Perch.app/Contents/MacOS/perch hook"

SETTINGS="$HOME/.claude/settings.json"
mkdir -p "$HOME/.claude"
[ -f "$SETTINGS" ] || echo '{}' > "$SETTINGS"
cp "$SETTINGS" "$SETTINGS.perch-backup"

/usr/bin/python3 - "$SETTINGS" "$HOOK" <<'PY'
import json, sys
path, cmd = sys.argv[1], sys.argv[2]
with open(path) as f:
    settings = json.load(f)
hooks = settings.setdefault("hooks", {})
events = ["SessionStart", "UserPromptSubmit", "PreToolUse", "PostToolUse",
          "Notification", "Stop", "SessionEnd"]
for event in events:
    groups = hooks.setdefault(event, [])
    already = any(h.get("command") == cmd for g in groups for h in g.get("hooks", []))
    if not already:
        groups.append({"hooks": [{"type": "command", "command": cmd}]})
with open(path, "w") as f:
    json.dump(settings, f, indent=2)
    f.write("\n")
print("Added Perch hooks to", path)
PY

echo "Backup of previous settings: $SETTINGS.perch-backup"
open /Applications/Perch.app
