#!/usr/bin/env bash
set -euo pipefail

# ----------------------------
# Config
# ----------------------------
PROJECT_MAIN="au.edu.adelaide.ds.assignment3.CouncilMember"
LOG_DIR="logs"
PIPES_DIR="pipes"
STARTUP_WAIT=5     # seconds: give members time to bind sockets
SCENARIO_TIME=20   # seconds: allow time for consensus/logging per scenario

# ----------------------------
# Build once
# ----------------------------
mvn -q -DskipTests compile

# ----------------------------
# State (pids + mapping)
# ----------------------------
declare -a PIDS                        # all pids to cleanup
declare -A PID_MAP=()                  # PID_MAP["M4"]=12345

mkdir -p "$LOG_DIR" "$PIPES_DIR"

banner () { echo -e "\n====================== $1 ======================\n"; }

cleanup_all () {
  # kill all children we started
  for pid in "${PIDS[@]:-}"; do
    kill "$pid" 2>/dev/null || true
  done
  # brief grace period, then force if needed
  sleep 1
  for pid in "${PIDS[@]:-}"; do
    kill -9 "$pid" 2>/dev/null || true
  done
  PIDS=()
  PID_MAP=()
  # remove any remaining pipes
  rm -f "${PIPES_DIR}"/*.in 2>/dev/null || true
}

# Start a member with optional FIFO for stdin
# usage: start_member M4 reliable logs/scenarioX/M4.log [fifo_path_or_empty]
start_member () {
  local id="$1" profile="$2" log="$3"
  local fifo="${4:-}"

  if [[ -n "$fifo" ]]; then
    # redirect member's stdin from the FIFO (so we can "type" later)
    nohup mvn -q exec:java \
      "-Dexec.mainClass=${PROJECT_MAIN}" \
      "-Dexec.args=${id} --profile=${profile}" \
      < "$fifo" > "$log" 2>&1 &
  else
    nohup mvn -q exec:java \
      "-Dexec.mainClass=${PROJECT_MAIN}" \
      "-Dexec.args=${id} --profile=${profile}" \
      > "$log" 2>&1 &
  fi

  local pid=$!
  PIDS+=("$pid")
  PID_MAP["$id"]=$pid
}

# convenience: make a FIFO if missing
ensure_fifo () {
  local fp="$1"
  [[ -p "$fp" ]] || mkfifo "$fp"
}

# convenience: write a line into FIFO (proposal value)
# this is equivalent to typing into the node’s console
propose_via_fifo () {
  local fifo="$1" value="$2"
  # Use subshell to avoid blocking if no reader yet
  ( echo "$value" > "$fifo" ) &
}

# kill just one member (used for M3 "crash")
kill_member () {
  local id="$1"
  local pid="${PID_MAP[$id]:-}"
  if [[ -n "${pid}" ]]; then
    kill "$pid" 2>/dev/null || true
    sleep 0.3
    kill -9 "$pid" 2>/dev/null || true
    unset PID_MAP["$id"]
  fi
}

# scenario scaffold: start 9 members with a profile map and optional fifo map
start_cluster () {
  local scenario="$1"; shift
  mkdir -p "${LOG_DIR}/${scenario}"

  # Start M1–M9 using global arrays (PROFILE_MAP and FIFO_MAP)
  for i in {1..9}; do
    local id="M${i}"
    local profile="${PROFILE_MAP[$id]:-reliable}"
    local log="${LOG_DIR}/${scenario}/${id}.log"
    local fifo="${FIFO_MAP[$id]:-}"

    start_member "$id" "$profile" "$log" "$fifo"
  done
}

# ----------------------------
# Scenario 1: Ideal network (all reliable)
# ----------------------------
: <<'SCENARIO1_DISABLED'
banner "SCENARIO 1: Ideal network (all reliable)"
mkdir -p "${LOG_DIR}/scenario1"

# Start all 9 members (reliable). M4 will propose after 3s.
for i in {1..9}; do
  id="M${i}"
  log="${LOG_DIR}/scenario1/${id}.log"

  if [[ "$i" == "4" ]]; then
    # Use helper so PID is tracked and we can clean up later.
    # We don’t need a FIFO here because we’re using --propose/--trigger-after.
    nohup mvn -q exec:java \
      "-Dexec.mainClass=${PROJECT_MAIN}" \
      "-Dexec.args=${id} --profile=reliable --propose=LEADER_M5 --trigger-after=3000" \
      > "$log" 2>&1 &
  else
    nohup mvn -q exec:java \
      "-Dexec.mainClass=${PROJECT_MAIN}" \
      "-Dexec.args=${id} --profile=reliable" \
      > "$log" 2>&1 &
  fi

  pid=$!
  PIDS+=("$pid")
  PID_MAP["$id"]=$pid
done

echo "All 9 members launched. Waiting ${STARTUP_WAIT}s for sockets..."
sleep "$STARTUP_WAIT"

echo "M4 will propose LEADER_M5 in ~3s (from process start). Allowing ${SCENARIO_TIME}s for consensus..."
sleep "$SCENARIO_TIME"

echo "Cleaning up Scenario 1 processes..."
cleanup_all
echo "Scenario 1 logs in ${LOG_DIR}/scenario1"

# ----------------------------
# Scenario 2: Concurrent proposals (M1 vs M8)
# ----------------------------
banner "SCENARIO 2: Concurrent proposals (M1 vs M8)"
cleanup_all
mkdir -p "${LOG_DIR}/scenario2"

# Launch all 9 members (all reliable)
for i in {1..9}; do
  id="M${i}"
  log="${LOG_DIR}/scenario2/${id}.log"

  case "$i" in
    1)
      # M1 proposes itself after 2s
      nohup mvn -q exec:java \
        "-Dexec.mainClass=${PROJECT_MAIN}" \
        "-Dexec.args=${id} --profile=reliable --propose=LEADER_M1 --trigger-after=2000" \
        > "$log" 2>&1 &
      ;;
    8)
      # M8 proposes itself after 2s (same as M1 to force conflict)
      nohup mvn -q exec:java \
        "-Dexec.mainClass=${PROJECT_MAIN}" \
        "-Dexec.args=${id} --profile=reliable --propose=LEADER_M8 --trigger-after=2000" \
        > "$log" 2>&1 &
      ;;
    *)
      nohup mvn -q exec:java \
        "-Dexec.mainClass=${PROJECT_MAIN}" \
        "-Dexec.args=${id} --profile=reliable" \
        > "$log" 2>&1 &
      ;;
  esac

  pid=$!
  PIDS+=("$pid")
  PID_MAP["$id"]=$pid
done

echo "Scenario 2: all 9 members launched. Waiting ${STARTUP_WAIT}s for sockets..."
sleep "$STARTUP_WAIT"
echo "M1 and M8 will both propose after ~2s. Allowing ${SCENARIO_TIME}s for consensus..."
sleep "$SCENARIO_TIME"

echo "Cleaning up Scenario 2 processes..."
cleanup_all
echo "Scenario 2 logs in ${LOG_DIR}/scenario2"

# ----------------------------
# Scenario 3a: Fault tolerance mix; M4 proposes
# ----------------------------
banner "SCENARIO 3a: Fault tolerance mix; M4 proposes"
mkdir -p "${LOG_DIR}/scenario3a"

# (Optional) give this one a little more time
SCEN3A_TIME=30

# Launch all 9 with mixed profiles; only M4 has a scheduled proposal
for i in {1..9}; do
  id="M${i}"
  log="${LOG_DIR}/scenario3a/${id}.log"

  case "$id" in
    M1) profile="reliable" ;;
    M2) profile="latent" ;;
    M3) profile="failure" ;;
    *)  profile="standard" ;;
  esac

  if [[ "$id" == "M4" ]]; then
    # M4 will propose after 2s
    nohup mvn -q exec:java \
      "-Dexec.mainClass=${PROJECT_MAIN}" \
      "-Dexec.args=${id} --profile=${profile} --propose=LEADER_M5 --trigger-after=2000" \
      > "$log" 2>&1 &
  else
    nohup mvn -q exec:java \
      "-Dexec.mainClass=${PROJECT_MAIN}" \
      "-Dexec.args=${id} --profile=${profile}" \
      > "$log" 2>&1 &
  fi

  pid=$!
  PIDS+=("$pid")
  PID_MAP["$id"]=$pid
done

echo "Scenario 3a cluster launched. Waiting ${STARTUP_WAIT}s for sockets..."
sleep "$STARTUP_WAIT"

echo "Allowing ${SCEN3A_TIME}s for consensus under latency/failure mix..."
sleep "$SCEN3A_TIME"

echo "Cleaning up Scenario 3a processes..."
cleanup_all
echo "Scenario 3a logs in ${LOG_DIR}/scenario3a"

# ----------------------------
# Scenario 3b: Fault tolerance mix; M2 (latent) proposes
# ----------------------------
banner "SCENARIO 3b: Fault tolerance mix; M2 (latent) proposes"
mkdir -p "${LOG_DIR}/scenario3b"

# Launch all 9 with the requested profile mix
for i in {1..9}; do
  id="M${i}"
  log="${LOG_DIR}/scenario3b/${id}.log"

  case "$i" in
    1) profile="reliable" ;;
    2) profile="latent"   ;;   # proposer (latent)
    3) profile="failure"  ;;
    *) profile="standard" ;;
  esac

  if [[ "$i" == "2" ]]; then
    # M2 (latent) proposes after ~2s
    nohup mvn -q exec:java \
      "-Dexec.mainClass=${PROJECT_MAIN}" \
      "-Dexec.args=${id} --profile=${profile} --propose=LEADER_M2 --trigger-after=2000" \
      > "$log" 2>&1 &
  else
    nohup mvn -q exec:java \
      "-Dexec.mainClass=${PROJECT_MAIN}" \
      "-Dexec.args=${id} --profile=${profile}" \
      > "$log" 2>&1 &
  fi

  pid=$!
  PIDS+=("$pid")
  PID_MAP["$id"]=$pid
done

echo "Scenario 3b cluster launched. Waiting ${STARTUP_WAIT}s for sockets..."
sleep "$STARTUP_WAIT"

echo "Allowing ${SCENARIO_TIME}s for consensus under latency/failure mix..."
sleep "$SCENARIO_TIME"

echo "Cleaning up Scenario 3b processes..."
cleanup_all
echo "Scenario 3b logs in ${LOG_DIR}/scenario3b"
SCENARIO1_DISABLED

# ----------------------------
# Scenario 3c: M3 proposes then crashes; others recover
# ----------------------------
banner "SCENARIO 3c: Fault tolerance mix; M3 proposes then crashes"

SC3_DIR="${LOG_DIR}/scenario3c"
mkdir -p "${SC3_DIR}"

# profiles per member (M1 reliable, M2 latent, M3 failure, M4–M9 standard)
declare -A PROFILE_MAP=(
  [M1]=reliable
  [M2]=latent
  [M3]=failure
  [M4]=standard
  [M5]=standard
  [M6]=standard
  [M7]=standard
  [M8]=standard
  [M9]=standard
)

# Launch all 9. Give M3 a scheduled proposal, then crash it.
# Also schedule M4 to start a new election later, to ensure recovery.
for i in {1..9}; do
  id="M${i}"
  profile="${PROFILE_MAP[$id]}"
  log="${SC3_DIR}/${id}.log"

  # default args
  args="${id} --profile=${profile}"

  # M3: propose LEADER_M3 at 1000 ms, then we'll kill it shortly after
  if [[ "$id" == "M3" ]]; then
    args="${args} --propose=LEADER_M3 --trigger-after=1000"
  fi

  # M4: recovery proposer — propose LEADER_M4 at 3000 ms
  if [[ "$id" == "M4" ]]; then
    args="${args} --propose=LEADER_M4 --trigger-after=3000"
  fi

  nohup mvn -q exec:java \
    "-Dexec.mainClass=${PROJECT_MAIN}" \
    "-Dexec.args=${args}" \
    > "$log" 2>&1 &

  pid=$!
  PIDS+=("$pid")
  PID_MAP["$id"]=$pid
done

echo "Scenario 3c cluster launched. Waiting ${STARTUP_WAIT}s for sockets..."
sleep "$STARTUP_WAIT"

# Let M3 send PREPARE, then crash it.
# Kill about 1.6s after its scheduled trigger so it has time to print PREPARE.
KILL_AFTER_MS=1600
sleep 1.6
echo "Crashing M3 now (after ~${KILL_AFTER_MS}ms past trigger)..."
kill_member "M3"

# Allow time for M4 to drive consensus after M3's crash
echo "Allowing ${SCENARIO_TIME}s for recovery consensus..."
sleep "$SCENARIO_TIME"

echo "Cleaning up Scenario 3c processes..."
cleanup_all
echo "Scenario 3c logs in ${SC3_DIR}"
