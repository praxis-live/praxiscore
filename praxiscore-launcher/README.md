# Machine-readable terminal I/O

`praxis --machine` starts an automation terminal instead of the human terminal
provider. It forces interactive input on, including when `--interactive=false`
is also supplied. Other startup options retain their existing behavior.
No MCP, JSON or external transport dependency is required.

## Framing

All frames are single physical lines, terminated by a newline:

| Direction | Frame |
| --- | --- |
| Runtime → client | `PRAXIS_READY` after the script service is located |
| Client → runtime | Standard base64 encoding of one complete UTF-8 PCL script |
| Runtime → client | `PRAXIS_OK <base64 UTF-8 result>` |
| Runtime → client | `PRAXIS_ERROR <base64 UTF-8 error>` |

Multiline scripts and results remain inside one base64 frame. Invalid base64 or
UTF-8 input produces an error frame; the next line can still be submitted. The
empty base64 payload is valid. A result payload is the existing terminal string
representation of the returned values, joined with a single space. It does not
preserve typed argument boundaries.

Requests are evaluated serially, including when input is pipelined. Replies are
matched to the pending script call before being written. A script that does not
complete blocks subsequent requests; the process owner should enforce a timeout
and terminate the process if necessary.

Normal runtime output may still appear on stdout/stderr. Clients should recognize
the reserved `PRAXIS_READY`, `PRAXIS_OK ` and `PRAXIS_ERROR ` lines and send other
output to their own logging. Trusted scripts must not write forged protocol
frames to stdout. This is a local automation adapter, not a sandbox or an
authenticated network service.

EOF stops the input reader without shutting down the hub. The automation client
owns the process lifecycle. The input thread is a daemon and does not close the
process-wide input or output streams.

## Minimal client

Build the distribution, then run this Python 3 example from the repository root:

```python
import base64
import subprocess

process = subprocess.Popen(
    ["praxiscore-bin/target/praxiscore/bin/praxis", "--machine"],
    stdin=subprocess.PIPE, stdout=subprocess.PIPE, text=True,
)
try:
    while process.stdout.readline().rstrip("\n") != "PRAXIS_READY":
        if process.poll() is not None:
            raise RuntimeError("Runtime exited before becoming ready")
    script = 'set greeting "Hello from an automation client"\nget greeting'
    process.stdin.write(base64.b64encode(script.encode("utf-8")).decode("ascii") + "\n")
    process.stdin.flush()
    for line in process.stdout:
        if line.startswith(("PRAXIS_OK ", "PRAXIS_ERROR ")):
            kind, payload = line.rstrip("\n").split(" ", 1)
            print(kind, base64.b64decode(payload).decode("utf-8"))
            break
finally:
    process.terminate()
    process.wait(timeout=5)
```

Production clients should add startup/request timeouts and kill fallback. Scripts
execute with the same privileges as the runtime process.

## Tests

`MachineTerminalIOTest` runs the transport inside a real hub with a test script
service. It checks multiline/Unicode/empty results, invalid input, script errors,
EOF behavior and ordered pipelined requests without changing global System I/O.
Run `./mvnw -pl praxiscore-launcher -am test` from the repository root.
