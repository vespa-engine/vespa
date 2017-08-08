# Hardware Verification
Verification of behaviour and performance of hardware. Benchmarks cpu frequency, disk write speed and memory write/read speed.
A report is sent to the node repository if any of the results are below an accepted threshold.

## Code Walkthrough
The main class, HardwareBenchmarker, calls every benchmark in the benchmark package. The results of these benchmarks are passed through
the BenchmarkResultInstructor, which creates a report containing the values below the accepted threshold. If the report is non-empty,
the node repository is updated with the poor benchmark result.