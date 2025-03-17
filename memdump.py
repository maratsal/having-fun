import os
import sys
import re

if __name__ == "__main__":
    pid = 1025
    print(f"Target PID: {pid}")

    map_path = f"/proc/{pid}/maps"
    mem_path = f"/proc/{pid}/mem"

    with open(map_path, 'r') as map_f, open(mem_path, 'rb', 0) as mem_f:
        for line in map_f.readlines():  # For each mapped region
            m = re.match(r'([0-9A-Fa-f]+)-([0-9A-Fa-f]+) ([-r])', line)
            if m and m.group(3) == 'r':  # Readable region
                start = int(m.group(1), 16)
                end = int(m.group(2), 16)

                # Hotfix: Avoid OverflowError on large addresses
                if start > sys.maxsize:
                    continue

                try:
                    mem_f.seek(start)  # Seek to region start
                    chunk = mem_f.read(end - start)  # Read region contents
                    sys.stdout.buffer.write(chunk)
                except OSError:
                    continue
