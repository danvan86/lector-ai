#!/usr/bin/env bash
# PreToolUse hook: agents never commit or push on main (or on a detached HEAD). Work happens on a feature branch.
# Exit code 2 blocks the tool call and shows stderr to the agent.
command=$(jq -r '.tool_input.command // ""')
if ! grep -Eq '(^|[^[:alnum:]_-])git([[:space:]]+-[cC][[:space:]]+[^[:space:]]+)*[[:space:]]+(commit|push)([[:space:]]|$)' <<<"$command"; then
  exit 0
fi
branch=$(git -C "${CLAUDE_PROJECT_DIR:-.}" branch --show-current 2>/dev/null)
case "$branch" in
  main | master | "")
    echo "Blocked: no commits or pushes on '${branch:-detached HEAD}'. Create or switch to a feature branch first (see AGENTS.md)." >&2
    exit 2
    ;;
esac
exit 0
