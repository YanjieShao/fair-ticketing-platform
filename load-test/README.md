# Load test harness

HTTP stampede against a running API. Method, knobs, and per-strategy
results: [docs/load-test.md](../docs/load-test.md).

The API must be started with `FT_LOADTEST_ENABLED=true` and the waiting
room left off. Wait 30–60 seconds between two 10k profiles so macOS
`TIME_WAIT` sockets can drain.

```bash
./run.sh smoke          # 500 buyers, 100 tickets
./run.sh contention     # 10k vs 3k
./run.sh target         # 10k vs 30k
```
