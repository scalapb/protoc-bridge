import sys
import time

total_bytes = 0
input_file = sys.argv[1] if len(sys.argv) > 1 else "/dev/stdin"
output_file = sys.argv[2] if len(sys.argv) > 2 else "/dev/stdout"

sys.stderr.write(f"[{time.time_ns()}][dd.py] Opening input stream\n")
with open(input_file, "rb") as input_stream:
    sys.stderr.write(f"[{time.time_ns()}][dd.py] Opened input stream\n")

    sys.stderr.write(f"[{time.time_ns()}][dd.py] Opening output stream\n")
    with open(output_file, "wb") as output_stream:
        sys.stderr.write(f"[{time.time_ns()}][dd.py] Opened output stream\n")

        while True:
            chunk = input_stream.read(4096)
            if not chunk:
                break
            output_stream.write(chunk)
            total_bytes += len(chunk)
            sys.stderr.write(f"[{time.time_ns()}][dd.py] Transferred {len(chunk)} bytes, total {total_bytes}\n")

sys.stderr.write(f"[{time.time_ns()}][dd.py] Transferred total {total_bytes} bytes\n")
