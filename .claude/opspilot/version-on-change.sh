#!/bin/sh
# OpsPilot: .claude 변경 자동 버전 강제 (Claude Code PostToolUse).
root=$(git rev-parse --show-toplevel 2>/dev/null) || exit 0
cd "$root" || exit 0
dirty=0
git diff --quiet -- .claude || dirty=1
git diff --cached --quiet -- .claude || dirty=1
[ -n "$(git ls-files --others --exclude-standard -- .claude)" ] && dirty=1
if [ "$dirty" = "1" ]; then
  git add -- .claude
  git -c user.email=opspilot@local -c user.name=OpsPilot \
    commit -m "ops(.claude): auto-version on change

[opspilot hook]" -- .claude >/dev/null 2>&1 || true
fi
exit 0
