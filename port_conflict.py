import os
import socket
import subprocess
import sys

def is_port_in_use(port, current_pid):
    """
    Check if the given port is in use by other processes, excluding the current process.
    
    :param port: Port number to check
    :param pid: Current process ID to exclude from the result
    :return: True if the port is in use by another process, False otherwise
    """
    try:
        # Run lsof command to check if any process is using the port
        result = subprocess.run(
            ['lsof', '-i', f':{port}', '-t'],
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True
        )
        output = result.stdout.strip()

        if output:
            # Check if the output contains lines with processes other than the current one
            return [
                line
                for line in output.split('\n')
                if line != str(current_pid)
            ]
        return []
    except subprocess.CalledProcessError as e:
        print(f"Error checking port: {e}", file=sys.stderr)
        return []

def main():
    repeat_count = 10000

    current_pid = os.getpid()  # Get the current process ID
    port_conflict_count = {}

    for i in range(1, repeat_count + 1):
        if i % 100 == 1:
            print(f"Running iteration {i} of {repeat_count}")

        # Bind to an available port (port 0)
        sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        sock.bind(('', 0))  # Bind to port 0 to get an available port
        port = sock.getsockname()[1]  # Get the actual port number assigned

        # Check if the port is in use by any other process
        pids = is_port_in_use(port, current_pid)
        if pids:
            print(f"Port conflict detected on port {port} with PIDs: {', '.join(pids)}", file=sys.stderr)
            port_conflict_count[port] = port_conflict_count.get(port, 0) + 1

        # Close the socket after checking
        sock.close()

    if port_conflict_count:
        print("Ports that were found to be in use and their collision counts:")
        for port, count in port_conflict_count.items():
            print(f"Port {port} was found in use {count} times")
    else:
        print("No ports were found to be in use.")

if __name__ == '__main__':
    main()
