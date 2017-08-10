# Hardware Verification
Verification of behaviour and performance of hardware. Benchmarks cpu frequency, disk write speed and memory write/read speed.
A report is sent to the node repository if any of the results are below an accepted threshold.

## Code Walkthrough
The main class, HardwareBenchmarker, calls every benchmark in the benchmark package. The results of these benchmarks are passed through
the BenchmarkResultInspector, which creates a BenchmarkReport containing the values below the accepted threshold. ReportSender is then called such that
the node repository is updated with the new benchmark results.