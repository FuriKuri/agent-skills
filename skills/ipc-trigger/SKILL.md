---
name: ipc-trigger
description: "Type a prompt directly into another Claude Code session running in a tmux pane — locally or on a remote host via SSH. Use this skill when multiple projects are being worked on simultaneously via Claude Code and the sessions need to communicate or coordinate through tmux. Trigger when the user wants to send a prompt to another session, coordinate between projects, or delegate work to a Claude instance in another pane. Also trigger when the user mentions cross-project communication, inter-session messaging, or tmux IPC."
allowed-tools:
  - Bash
---

# IPC Trigger

Send a prompt directly into another tmux pane running Claude Code — locally or on a remote host.

## Usage

Parse `$ARGUMENTS`: first word is the target, the rest is the prompt.

The target can be:
- `frontend` — local pane matched by directory basename
- `frontend@devbox` — remote pane on host `devbox` (SSH hostname, IP, or Tailscale MagicDNS name)

## List Available Panes (no arguments)

If no arguments are given, list available local panes and highlight which ones run Claude Code:

```bash
SELF=$(tmux display-message -p '#{pane_id}' 2>/dev/null || echo "")
tmux list-panes -a -F '#{session_name}:#{window_index}.#{pane_index} #{pane_id} #{pane_current_path} #{pane_current_command}' | while read -r label id path cmd; do
  [ "$id" = "$SELF" ] && continue
  basename=$(basename "$path")
  if echo "$cmd" | grep -qi "claude\|2\.1\.\|node"; then
    echo "$basename  ->  $path  ($label)  [Claude Code running]"
  else
    echo "$basename  ->  $path  ($label)  [$cmd]"
  fi
done
```

## Remote Target (contains `@`)

Split into target name and host:

```bash
TARGET="<part before @>"
HOST="<part after @>"
PROMPT="<rest of arguments>"
PANE_ID=$(ssh "$HOST" tmux list-panes -a -F '#{pane_id} #{pane_current_path}' | while read -r id path; do
  [ "$(basename "$path")" = "$TARGET" ] && echo "$id" && break
done)
if [ -z "$PANE_ID" ]; then
  echo "Error: no pane '$TARGET' found on $HOST"
  exit 1
fi
ssh "$HOST" tmux send-keys -t "$PANE_ID" "$PROMPT" Enter
```

## Local Target (no `@`)

```bash
TARGET="<first word from arguments>"
PROMPT="<rest of arguments>"
SELF=$(tmux display-message -p '#{pane_id}' 2>/dev/null || echo "")
PANE_ID=$(tmux list-panes -a -F '#{pane_id} #{pane_current_path}' | while read -r id path; do
  [ "$id" = "$SELF" ] && continue
  [ "$(basename "$path")" = "$TARGET" ] && echo "$id" && break
done)
if [ -z "$PANE_ID" ]; then
  echo "Error: no pane found for '$TARGET'"
  exit 1
fi
tmux send-keys -t "$PANE_ID" "$PROMPT" Enter
```

## Paste Confirmation

Claude Code shows multi-line pastes as `[Pasted text #N +N lines]` and requires an extra Enter to submit. After sending a long prompt:

```bash
sleep 1
tmux send-keys -t "$PANE_ID" Enter
```

Always send this extra Enter after the initial `send-keys ... Enter` to handle the paste confirmation. Wait 1 second between the two to allow the terminal to register the paste.

## Waiting for Response

To wait for the other session to finish processing and capture the result:

```bash
# Poll until the Claude session returns to the idle prompt (❯)
for i in $(seq 1 120); do
  sleep 5
  OUTPUT=$(tmux capture-pane -t "$PANE_ID" -p -S -50)
  # Check if Claude finished: idle prompt visible and no spinner
  if echo "$OUTPUT" | tail -5 | grep -qE '^[─]+$|^❯\s*$'; then
    echo "$OUTPUT"
    break
  fi
done
```

Use this when you need to verify the result of the prompt you sent.

## Conflict Resolution

If multiple panes match, list them all with session labels and pane IDs and ask the user which one to target.

## Safety

- Only use when the target pane is idle and ready for input (check for the `❯` prompt before sending).
- Verify the target pane is running Claude Code by checking `pane_current_command`.
- For destructive or irreversible prompts, confirm with the user before sending.
