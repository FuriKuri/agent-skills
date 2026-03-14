---
name: claude-ipc
description: "Communicate with other Claude Code sessions running in tmux panes — send prompts, wait for completion, and capture results. Use this skill when you need to delegate work to another Claude Code instance, coordinate across projects, send fix instructions to a session in another repo, or retrieve output from a parallel session. Trigger when the user mentions sending something to another Claude session, cross-project coordination, tmux communication, or asking another Claude to do something."
allowed-tools:
  - Bash
---

# Claude IPC

Send prompts to other Claude Code sessions via tmux, wait for them to finish, and capture the results.

## Usage

Parse `$ARGUMENTS`: first token is the target, the rest is the prompt.

- Target is matched against the **directory basename** of each tmux pane's working directory (e.g., `backend`, `frontend`, `my-project`).
- If the target contains `@`, the part after `@` is treated as an SSH host (e.g., `backend@devbox`).

## Algorithm

### Step 1 — Discover Claude Sessions

List all tmux panes and identify which ones are running Claude Code:

```bash
SELF=$(tmux display-message -p '#{pane_id}' 2>/dev/null || echo "")
tmux list-panes -a \
  -F '#{session_name}:#{window_index}.#{pane_index} #{pane_id} #{pane_current_path} #{pane_current_command}' \
  | while read -r label id path cmd; do
    [ "$id" = "$SELF" ] && continue
    basename=$(basename "$path")
    # Claude Code runs as node or as a process whose name contains "claude"
    if echo "$cmd" | grep -qiE 'claude|node|2\.[0-9]+\.[0-9]+'; then
      echo "$basename  ->  $path  ($label)  [Claude Code]"
    else
      echo "$basename  ->  $path  ($label)  [$cmd]"
    fi
  done
```

If no arguments were provided, print this list and stop — let the user pick a target.

### Step 2 — Resolve Target Pane

Find the tmux session matching the target directory name:

```bash
TARGET="<first token from arguments>"
PROMPT="<remaining arguments>"

SELF=$(tmux display-message -p '#{pane_id}' 2>/dev/null || echo "")
MATCHES=()
while read -r label id path cmd; do
  [ "$id" = "$SELF" ] && continue
  [ "$(basename "$path")" = "$TARGET" ] && MATCHES+=("$label $id $path")
done < <(tmux list-panes -a -F '#{session_name}:#{window_index}.#{pane_index} #{pane_id} #{pane_current_path} #{pane_current_command}')
```

- **0 matches** → error, show available panes.
- **1 match** → use it.
- **Multiple matches** → list them all and ask the user which one.

For **remote targets** (`target@host`), run the same discovery over SSH:

```bash
ssh "$HOST" tmux list-panes -a -F '#{pane_id} #{pane_current_path}' | ...
```

### Step 3 — Verify Target is Idle

Before sending anything, capture the target pane and check it is ready for input:

```bash
OUTPUT=$(tmux capture-pane -t "$PANE_ID" -p -S -10)
```

Look for an idle `❯` prompt at the bottom. If the target is busy (spinner visible, no prompt), tell the user and wait or abort.

### Step 4 — Send the Prompt

```bash
tmux send-keys -t "$PANE_ID" "$PROMPT" Enter
```

**Paste confirmation**: Claude Code wraps long or multi-line input as `[Pasted text #N +N lines]` and waits for an extra Enter. Always send it:

```bash
sleep 1
tmux send-keys -t "$PANE_ID" Enter
```

### Step 5 — Wait for Completion

Poll the target pane until the Claude session finishes processing. Claude Code is idle when the pane shows the separator line (`─────...`) followed by an empty `❯` prompt:

```bash
MAX_WAIT=120   # iterations (× 5s = 10 minutes max)
for i in $(seq 1 "$MAX_WAIT"); do
  sleep 5
  SNAPSHOT=$(tmux capture-pane -t "$PANE_ID" -p -S -50)

  # Idle indicators: separator bar + empty prompt at the bottom
  if echo "$SNAPSHOT" | tail -8 | grep -qE '^[─]{10,}$'; then
    if echo "$SNAPSHOT" | tail -4 | grep -qE '^❯\s*$'; then
      break
    fi
  fi
done
```

### Step 6 — Capture and Return the Result

Grab the final output from the target pane:

```bash
RESULT=$(tmux capture-pane -t "$PANE_ID" -p -S -80)
echo "$RESULT"
```

Parse the captured output to extract the relevant response (everything between the sent prompt and the final `❯` prompt).

Report the result back to the user: summarise what the other session did, whether it succeeded, and any relevant output.

## Remote Targets

For targets with `@` (e.g., `backend@devbox`):

```bash
TARGET="${ARG%%@*}"
HOST="${ARG#*@}"

# Discovery
PANE_ID=$(ssh "$HOST" tmux list-panes -a -F '#{pane_id} #{pane_current_path}' | while read -r id path; do
  [ "$(basename "$path")" = "$TARGET" ] && echo "$id" && break
done)

# Send
ssh "$HOST" tmux send-keys -t "$PANE_ID" "$PROMPT" Enter
sleep 1
ssh "$HOST" tmux send-keys -t "$PANE_ID" Enter

# Capture
ssh "$HOST" tmux capture-pane -t "$PANE_ID" -p -S -80
```

## Safety

- **Always verify idle state** before sending — never interrupt a session that is mid-task.
- **Confirm destructive prompts** with the user before sending (e.g., "delete", "reset", "drop").
- **Report back** what the other session did — the user should always know what happened.
