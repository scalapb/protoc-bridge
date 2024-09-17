#!/usr/bin/env bash

BYTE_LENGTH="$1"
SERVER_MODE="${2:-nc-save}"
CLIENT_MODE="${3:-nc}"

TEST_FILE_PATH="/tmp/domain_socket_test_file"
SOCKET_PATH="/tmp/domain_socket_test.sck"
SERVER_RESULT_PATH="$TEST_FILE_PATH.server"
CLIENT_RESULT_PATH="$TEST_FILE_PATH.client"
dd if=/dev/urandom of="$TEST_FILE_PATH" bs=1 count="$BYTE_LENGTH" 2>/dev/null

if [[ "$SERVER_MODE" == *"-save" ]]; then
  TEST_RESULT_PATH="$SERVER_RESULT_PATH"
else
  TEST_RESULT_PATH="$CLIENT_RESULT_PATH"
fi

test_socket() {
  # Start a process to consume the data from the socket
  if [[ "$SERVER_MODE" == "nc-save" ]]; then
    (nc -l -U "$SOCKET_PATH" > "$SERVER_RESULT_PATH" && echo "Completed saving random bytes from the socket") &
  elif [[ "$SERVER_MODE" == "ncat-save" ]]; then
    (ncat -l -U "$SOCKET_PATH" > "$SERVER_RESULT_PATH" && echo "Completed saving random bytes from the socket") &
  elif [[ "$SERVER_MODE" == "socat-save" ]]; then
    (socat UNIX-LISTEN:"$SOCKET_PATH" - > "$SERVER_RESULT_PATH" && echo "Completed saving random bytes from the socket") &
  elif [[ "$SERVER_MODE" == "socat-echo" ]]; then
    (socat UNIX-LISTEN:"$SOCKET_PATH" EXEC:"/bin/cat" && echo "Completed echoing random bytes from the socket") &
  else
    echo "Invalid server mode: $SERVER_MODE"
    exit 1
  fi
  SERVER_PID=$!
  echo "Starting the server (PID: $SERVER_PID)"

  # Wait for the socket file to be created so that the server has started
  while [ ! -e "$SOCKET_PATH" ]; do
    sleep 0.001
  done
  echo "The server has started and is listening to the socket (PID: $SERVER_PID)"

  # `nc` can fail even if we wait for another second to ensure the server has started
  # sleep 1

  # Start dumping random bytes to the socket in the background
  if [[ "$CLIENT_MODE" == "nc" ]]; then
    (nc -U "$SOCKET_PATH" < "$TEST_FILE_PATH" > "$CLIENT_RESULT_PATH" && echo "Completed dumping random bytes to the socket") &
  elif [[ "$CLIENT_MODE" == "ncat" ]]; then
    (ncat -U "$SOCKET_PATH" < "$TEST_FILE_PATH" > "$CLIENT_RESULT_PATH" && echo "Completed dumping random bytes to the socket") &
  elif [[ "$CLIENT_MODE" == "socat" ]]; then
    (socat - UNIX-CONNECT:"$SOCKET_PATH" < "$TEST_FILE_PATH" > "$CLIENT_RESULT_PATH" && echo "Completed dumping random bytes to the socket") &
  else
    echo "Invalid client mode: $CLIENT_MODE"
    exit 1
  fi
  CLIENT_PID=$!
  echo "Started dumping random bytes to the socket (PID: $CLIENT_PID)"

  # Ensure the client process is killed
  wait $CLIENT_PID 2>/dev/null
  echo "The client process has stopped (PID: $CLIENT_PID)"

  # Ensure the server process is killed
  wait $SERVER_PID 2>/dev/null
  echo "The server process has stopped (PID: $SERVER_PID)"

  # Check the size of the data read from the socket
  DATA_SIZE=$(wc -c < "$TEST_RESULT_PATH")
  if [ "$DATA_SIZE" -ne "$BYTE_LENGTH" ]; then
    echo "Error: Expected $BYTE_LENGTH bytes, but read $DATA_SIZE bytes"
    exit 1
  else
    echo "Successfully read $BYTE_LENGTH bytes from the socket"
  fi

  rm -f "$SOCKET_PATH"
  rm -f "$SERVER_RESULT_PATH"
  rm -f "$CLIENT_RESULT_PATH"
}

rm -f "$SOCKET_PATH"

# Repeat the process
counter=0;
while test_socket; do
  ((counter++)); echo "Iterations completed: $counter";
done
echo "Command failed after $counter successful iterations."
