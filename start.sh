#!/usr/bin/env bash
set -e

ROOT="$(cd "$(dirname "$0")" && pwd)"
BACKEND="$ROOT/backend"
FRONTEND="$ROOT/frontend/index.html"

# ── Pre-flight: check sklearn is available ────────────────────────────────
echo "[start] Checking Python/sklearn availability..."
if ! python3 -c "import sklearn, joblib" 2>/dev/null; then
  echo ""
  echo "  *** WARNING: scikit-learn or joblib not found! ***"
  echo "  ML models will not run and threat confidence will show 0.0%."
  echo "  Fix before the demo:"
  echo "    python3 -m pip install scikit-learn joblib"
  echo ""
  echo "  Continuing anyway — game will fall back to random threat selection."
  echo ""
fi

# ── Build ─────────────────────────────────────────────────────────────────
echo "[start] Building backend..."
mvn -f "$BACKEND/pom.xml" package -q

# ── Kill any stale process on port 8887 ──────────────────────────────────
STALE=$(lsof -ti :8887 2>/dev/null || true)
if [ -n "$STALE" ]; then
  echo "[start] Killing stale process on port 8887 (PID $STALE)..."
  kill "$STALE" 2>/dev/null || true
  sleep 0.5
fi

# ── Launch backend ────────────────────────────────────────────────────────
echo "[start] Starting backend..."
cd "$ROOT"
java -cp "$BACKEND/target/cyber-training-game.jar:$BACKEND/target/libs/*" Main \
  > "$ROOT/backend.log" 2>&1 &
BACKEND_PID=$!
echo "[start] Backend PID: $BACKEND_PID"

# Kill backend when this script exits (Ctrl-C or terminal close)
trap "echo '[start] Stopping backend...'; kill $BACKEND_PID 2>/dev/null" EXIT

# ── Wait for WebSocket port 8887 to be ready ─────────────────────────────
echo "[start] Waiting for WebSocket on port 8887..."
for i in $(seq 1 30); do
  if bash -c "echo > /dev/tcp/localhost/8887" 2>/dev/null; then
    echo "[start] Backend ready."
    break
  fi
  sleep 0.5
  if [ "$i" -eq 30 ]; then
    echo "[start] ERROR: Backend did not start in time. Check backend.log"
    exit 1
  fi
done

# ── Open frontend ─────────────────────────────────────────────────────────
echo "[start] Opening $FRONTEND"
if command -v xdg-open &>/dev/null; then
  xdg-open "$FRONTEND"
elif command -v open &>/dev/null; then
  open "$FRONTEND"
else
  echo "[start] Could not detect a browser opener. Open manually: $FRONTEND"
fi

# ── Keep running (backend log tailed in foreground) ───────────────────────
echo "[start] Tailing backend.log — press Ctrl-C to stop."
tail -f "$ROOT/backend.log"
