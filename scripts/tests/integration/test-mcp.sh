#!/bin/bash
set -euo pipefail
BASE_URL=${BASE_URL:-http://127.0.0.1:8080}
MCP_PATH=${MCP_PATH:-/mcp}
MCP_TOKEN=${MCP_TOKEN:-}
FAKE_URL=${FAKE_URL:-http://127.0.0.1:18089}

# Loopback-only MCP contract check against the fake OpenAI-compatible server and the
# isolated Chroma corpus. Requires the backend started with AMA_MCP_ENABLED=true and a
# real AMA_MCP_AUTH_TOKEN; the token is read from the environment, never logged.
[ -n "$MCP_TOKEN" ] || { echo "MCP_TOKEN is required"; exit 1; }

MCP_URL="$BASE_URL$MCP_PATH"
AUTH_HEADER="Authorization: Bearer $MCP_TOKEN"
HEADERS_FILE=$(mktemp)
trap 'rm -f "$HEADERS_FILE"' EXIT

rpc() {
  local id=$1 method=$2 params=${3:-'{}'}
  curl -s -D "$HEADERS_FILE" -o - -X POST "$MCP_URL" \
    -H "Content-Type: application/json" \
    -H "Accept: application/json, text/event-stream" \
    -H "$AUTH_HEADER" \
    ${SESSION_HEADER:+-H "$SESSION_HEADER"} \
    -d "{\"jsonrpc\":\"2.0\",\"id\":$id,\"method\":\"$method\",\"params\":$params}"
}

notify() {
  curl -s -o /dev/null -X POST "$MCP_URL" \
    -H "Content-Type: application/json" \
    -H "Accept: application/json, text/event-stream" \
    -H "$AUTH_HEADER" \
    ${SESSION_HEADER:+-H "$SESSION_HEADER"} \
    -d "{\"jsonrpc\":\"2.0\",\"method\":\"$1\"}"
}

extract_message() {
  # Streamable HTTP answers with SSE (data: lines, space optional); fall back to a
  # plain JSON body. Prints the JSON-RPC payload for the requested id.
  local body=$1 id=$2
  local selected
  selected=$(printf '%s\n' "$body" | sed -n 's/^data:[[:space:]]*//p' | grep "\"id\":$id," | tail -1)
  if [ -z "$selected" ]; then
    selected=$(printf '%s\n' "$body" | grep "\"id\":$id," | tail -1)
  fi
  [ -n "$selected" ] || { echo "No JSON-RPC response for id $id in: $body" >&2; exit 1; }
  if echo "$selected" | jq -e 'has("error")' >/dev/null; then
    echo "JSON-RPC error for id $id: $selected" >&2; exit 1
  fi
  printf '%s' "$selected"
}

session_id() {
  tr -d '\r' < "$HEADERS_FILE" | sed -n 's/^[Mm]cp-[Ss]ession-[Ii]d: //p' | tail -1
}

# 1. initialize
BODY=$(rpc 1 initialize '{"protocolVersion":"2025-03-26","capabilities":{},"clientInfo":{"name":"ama-test-mcp.sh","version":"1.0"}}')
MESSAGE=$(extract_message "$BODY" 1)
[ "$(printf '%s' "$MESSAGE" | jq -r '.result.serverInfo.name')" = "aviation-maintenance-assistant" ] \
  || { echo "initialize did not report the expected server name: $MESSAGE"; exit 1; }
SESSION_ID=$(session_id)
SESSION_HEADER=${SESSION_ID:+"Mcp-Session-Id: $SESSION_ID"}

# 2. initialized notification, then the tool catalog
notify notifications/initialized
BODY=$(rpc 2 tools/list '{}')
MESSAGE=$(extract_message "$BODY" 2)
TOOL_NAMES=$(printf '%s' "$MESSAGE" | jq -r '.result.tools[].name')
echo "$TOOL_NAMES" | grep -qx 'ask_maintenance_question' \
  || { echo "tools/list does not offer ask_maintenance_question: $MESSAGE"; exit 1; }
[ "$(printf '%s\n' "$TOOL_NAMES" | wc -l)" -eq 1 ] \
  || { echo "Expected exactly one MCP tool, got: $TOOL_NAMES"; exit 1; }

# 3. Corpus-hit call returns the original excerpt text and consumes exactly one fake call
curl -s -X POST "$FAKE_URL/__reset" >/dev/null
QUESTION="For B737-800 with CFM56-7B, what is the oil capacity of the PWR-40S?"
BODY=$(rpc 3 tools/call "{\"name\":\"ask_maintenance_question\",\"arguments\":{\"question\":\"$QUESTION\"}}")
MESSAGE=$(extract_message "$BODY" 3)
ANSWER=$(printf '%s' "$MESSAGE" | jq -r '.result.content[0].text')
[ "$(printf '%s' "$ANSWER" | jq -r '.outcome')" = "ANSWERED" ] \
  || { echo "tools/call did not answer: $ANSWER"; exit 1; }
FIRST_TEXT=$(printf '%s' "$ANSWER" | jq -r '.citations[0].text')
[ -n "$FIRST_TEXT" ] || { echo "citations carry no original excerpt text: $ANSWER"; exit 1; }
CALLS=$(curl -s "$FAKE_URL/__calls" | grep -o '"calls":[0-9]*' | cut -d: -f2)
[ "$CALLS" -eq 1 ] || { echo "Expected exactly 1 fake attempt, got $CALLS"; exit 1; }

# 4. Aircraft clarification short-circuits without any model call
BODY=$(rpc 4 tools/call '{"name":"ask_maintenance_question","arguments":{"question":"engine oil pressure"}}')
MESSAGE=$(extract_message "$BODY" 4)
ANSWER=$(printf '%s' "$MESSAGE" | jq -r '.result.content[0].text')
[ "$(printf '%s' "$ANSWER" | jq -r '.outcome')" = "CLARIFICATION_REQUIRED" ] \
  || { echo "clarification shortcut missing: $ANSWER"; exit 1; }
CALLS=$(curl -s "$FAKE_URL/__calls" | grep -o '"calls":[0-9]*' | cut -d: -f2)
[ "$CALLS" -eq 1 ] || { echo "Clarification must not call the model, fake attempts: $CALLS"; exit 1; }

# 5. Without the token the endpoint answers 401 before any JSON-RPC handling
STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$MCP_URL" \
  -H "Content-Type: application/json" \
  -H "Accept: application/json, text/event-stream" \
  -d '{"jsonrpc":"2.0","id":9,"method":"tools/list"}')
[ "$STATUS" -eq 401 ] || { echo "Expected 401 without token, got $STATUS"; exit 1; }

echo "test-mcp.sh PASS"
