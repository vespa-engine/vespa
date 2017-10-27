<!-- Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root. -->
# Hardware Verification
Verification of behaviour and performance of hardware. Benchmarks cpu frequency, disk write speed and memory write/read speed.
A report is sent to the node repository if any of the results are below an accepted threshold.

## Code Walkthrough
The main class, HardwareBenchmarker, calls every benchmark in the benchmark package. 

The results of these benchmarks are passed through
the BenchmarkResultInspector, which creates a BenchmarkReport containing the values below the accepted threshold. 

ReportSender is then called such that
the old HardwareDivergence report is retrieved from node repo and updated with the new results. ReportSender prints the new HardwareReport such that it 
can be updated in node repo by chef or other.
