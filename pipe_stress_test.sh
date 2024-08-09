#!/usr/bin/env bash

BYTE_LENGTH="$1"
READER_MODE="${2:-dd}"
WRITER_MODE="${3:-dd}"

TEST_FILE="$(mktemp -t protopipe)"
dd if=/dev/urandom of="$TEST_FILE" bs=1 count="$BYTE_LENGTH" 2>/dev/null

test_pipe() {
  # Create a unique temporary directory for the pipe
  PIPE_DIR=$(mktemp -d -t protopipe)
  PIPE_PATH="$PIPE_DIR/output"
  PIPE_DATA_PATH="$PIPE_DIR/pipe_data"

  # Create the named pipe
  mkfifo "$PIPE_PATH"
  echo "Created named pipe at $PIPE_PATH"

  # Start monitoring the pipe using fs_usage in the background
  # sudo fs_usage -w | grep "$PIPE_PATH" &
  # MONITOR_PID=$!
  # echo "Started monitoring the pipe $PIPE_PATH (PID: $MONITOR_PID)"

  # Start dumping random bytes to the pipe in the background
  if [[ "$READER_MODE" == "dd" ]]; then
    (dd if="$TEST_FILE" of="$PIPE_PATH" 2>/dev/null && echo "Completed dumping random bytes to the pipe") &
  elif [[ "$READER_MODE" == "dd.py" ]]; then
    (python3 "$(dirname "$0")"/dd.py "$TEST_FILE" "$PIPE_PATH" && echo "Completed dumping random bytes to the pipe") &
  elif [[ "$READER_MODE" == "cat" ]]; then
    (cat "$TEST_FILE" > "$PIPE_PATH" && echo "Completed dumping random bytes to the pipe") &
  else
    echo "Invalid reader mode: $READER_MODE"
    exit 1
  fi
  DUMP_PID=$!
  echo "Started dumping random bytes to the pipe (PID: $DUMP_PID)"

  # Randomize the sleep duration
  SLEEP_DURATION=$((RANDOM % 100))
  echo "Sleeping for: $SLEEP_DURATION milliseconds"
  sleep "$(echo "scale=3; $SLEEP_DURATION/1000" | bc)"

  # Start a process to consume the data from the pipe
  if [[ "$WRITER_MODE" == "dd" ]]; then
    (dd if="$PIPE_PATH" of="$PIPE_DATA_PATH" 2>/dev/null && echo "Completed consuming random bytes from the pipe") &
  elif [[ "$WRITER_MODE" == "dd.py" ]]; then
    (python3 "$(dirname "$0")"/dd.py "$PIPE_PATH" "$PIPE_DATA_PATH" && echo "Completed consuming random bytes from the pipe") &
  elif [[ "$WRITER_MODE" == "cat" ]]; then
    (cat "$PIPE_PATH" > "$PIPE_DATA_PATH" && echo "Completed consuming random bytes from the pipe") &
  else
    echo "Invalid writer mode: $WRITER_MODE"
    exit 1
  fi
  CONSUME_PID=$!
  echo "Started consuming data from the pipe (PID: $CONSUME_PID)"

  # Ensure the dumping process is killed
  wait $DUMP_PID 2>/dev/null
  echo "The dumping process has stopped (PID: $DUMP_PID)"

  # Ensure the consuming process is killed
  wait $CONSUME_PID 2>/dev/null
  echo "The consuming process has stopped (PID: $CONSUME_PID)"

  # Stop the monitoring
  # kill $MONITOR_PID 2>/dev/null
  # wait $MONITOR_PID 2>/dev/null
  # echo "Stopped monitoring the pipe (PID: $MONITOR_PID)"

  # Check the size of the data read from the pipe
  DATA_SIZE=$(wc -c < "$PIPE_DATA_PATH")
  if [ "$DATA_SIZE" -ne "$BYTE_LENGTH" ]; then
    echo "Error: Expected $BYTE_LENGTH bytes, but read $DATA_SIZE bytes"
    exit 1
  else
    echo "Successfully read $BYTE_LENGTH bytes from the pipe"
  fi

  # Remove the pipe
  rm "$PIPE_PATH"
  rm "$PIPE_DATA_PATH"

  # Remove the temporary directory
  rmdir "$PIPE_DIR"
}

# Kill existing fs_usage instances
# sudo pkill fs_usage

# Repeat the process
counter=0;
while test_pipe; do
  ((counter++)); echo "Iterations completed: $counter";
done
echo "Command failed after $counter successful iterations."
