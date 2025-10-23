#!/usr/bin/env bash
set -euo pipefail

PROJECT_MAIN="au.edu.adelaide.ds.assignment3.CouncilMember"
LOG_DIR="logs"
PORT_BASE=9001   # M1=9001 ... M9=9009
STARTUP_WAIT=5   # seconds to let nodes bind sockets
SCENARIO_TIME=20 # seconds to let consensus complete

mvn -q -DskipTests compile

mkdir -p "$LOG_DIR"/{scenario1,scenario2,scenario3a,scenario3b,scenario3c}

pids=()

start_member () {
  local id="$1" profile="$2" extra="$3" log="$4"
  # run maven exec in background and capture PID
  nohup mvn -q exec:java \
    "-Dexec.mainClass=${PROJECT_MAIN}" \
    "-Dexec.args=${id} --profile=${profile} ${extra}" \
    > "${log}" 2>&1 &
  pids+=($!)
}

kill_all () {
  for pid in "${pids[@]:-}"; do
    kill "$pid" 2>/dev/null || true
  done
  pids=()
  # give them a moment to exit cleanly, then force
  sleep 1
  for pid in $(ps -o pid= --ppid $$); do
    kill -9 "$pid" 2>/dev/null || true
  done
}

banner () {
  echo
  echo "====================== $1 ======================"
  echo
}

########################################
# Scenario 1: Ideal network (all reliable)
# Start all 9 first, then trigger proposal from M4 via stdin FIFO
########################################
banner "SCENARIO 1: Ideal network (all reliable)"

mkdir -p pipes
fifo_m4="pipes/M4.in"
[ -p "$fifo_m4" ] || mkfifo "$fifo_m4"

# Start all 9 members; M4 reads stdin from the FIFO
for i in {1..9}; do
  extra=""
  log="${LOG_DIR}/scenario1/M${i}.log"

  if [ "$i" -eq 4 ]; then
    # redirect M4's stdin from the FIFO (no --propose at startup)
    nohup mvn -q exec:java \
      "-Dexec.mainClass=${PROJECT_MAIN}" \
      "-Dexec.args=M4 --profile=reliable" \
      < "$fifo_m4" > "$log" 2>&1 &
    pids+=($!)
  else
    start_member "M${i}" reliable "$extra" "$log"
  fi
done

# Give everyone time to bind sockets
sleep "${STARTUP_WAIT}"

# Now trigger the proposal AFTER startup (same as typing in M4 console)
echo "LEADER_M5" > "$fifo_m4"

# Let consensus complete and logs flush
sleep "${SCENARIO_TIME}"

# Cleanup
kill_all
rm -f "$fifo_m4"

########################################
# Scenario 3a: Fault tolerance — M4 proposes
########################################
banner "SCENARIO 3a: Fault tolerance mix; M4 proposes"
# profiles: M1 (reliable), M2 (latent), M3 (failure), M4–M9 (standard)
start_member M1 reliable "" "${LOG_DIR}/scenario3a/M1.log"
start_member M2 latent   "" "${LOG_DIR}/scenario3a/M2.log"
start_member M3 failure  "" "${LOG_DIR}/scenario3a/M3.log"
for i in {4..9}; do start_member "M${i}" standard "" "${LOG_DIR}/scenario3a/M${i}.log"; done
sleep "${STARTUP_WAIT}"
start_member M4 standard "--propose=LEADER_M4" "${LOG_DIR}/scenario3a/M4-proposer.log"
sleep "${SCENARIO_TIME}"
kill_all

########################################
# Scenario 3b: Fault tolerance — M2 (latent) proposes
########################################
banner "SCENARIO 3b: Fault tolerance mix; M2 (latent) proposes"
start_member M1 reliable "" "${LOG_DIR}/scenario3b/M1.log"
start_member M2 latent   "" "${LOG_DIR}/scenario3b/M2.log"
start_member M3 failure  "" "${LOG_DIR}/scenario3b/M3.log"
for i in {4..9}; do start_member "M${i}" standard "" "${LOG_DIR}/scenario3b/M${i}.log"; done
sleep "${STARTUP_WAIT}"
start_member M2 latent "--propose=LEADER_M2" "${LOG_DIR}/scenario3b/M2-proposer.log"
sleep "${SCENARIO_TIME}"
kill_all

########################################
# Scenario 3c: Fault tolerance — M3 crashes after sending PREPARE
########################################
banner "SCENARIO 3c: Fault tolerance mix; M3 proposes then crashes"
start_member M1 reliable "" "${LOG_DIR}/scenario3c/M1.log"
start_member M2 latent   "" "${LOG_DIR}/scenario3c/M2.log"
start_member M3 failure  "" "${LOG_DIR}/scenario3c/M3.log"
for i in {4..9}; do start_member "M${i}" standard "" "${LOG_DIR}/scenario3c/M${i}.log"; done
sleep "${STARTUP_WAIT}"
# have M3 propose, then kill it to simulate a crash
start_member M3 failure "--propose=LEADER_M3" "${LOG_DIR}/scenario3c/M3-proposer.log"
sleep 3   # enough time to send PREPARE
# kill only the latest M3 proposer process (last pid in array)
kill "${pids[-1]}" 2>/dev/null || true
sleep 1
# now another member takes over and drives the election
start_member M1 reliable "--propose=LEADER_M1" "${LOG_DIR}/scenario3c/M1-proposer.log"
sleep "${SCENARIO_TIME}"
kill_all

echo
echo "All scenarios complete. Logs saved under: ${LOG_DIR}/"

