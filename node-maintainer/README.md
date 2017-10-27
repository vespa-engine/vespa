<!-- Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root. -->
# Node Admin Maintenance

Executes maintenance jobs, such as deleting old logs, processing and reporting coredumps, on behalf of node-admin. 
Node admin maintenance runs as a separate JVM from node-admin to make it possible to run it as root if needed.

## Node Verification
Node verification for both hardware and spec. Hardware is verified by performing different benchmarking tasks, 
while spec is verified by comparing information reported by the OS with the spec from node repository.

### Execute examples
Spec verification and hardware benchmarks must be executed with config server host name as parameter 

SpecVerifier:
- sudo java -cp node-maintainer-jar-with-dependencies.jar com.yahoo.vespa.hosted.node.verification.spec.SpecVerifier cfg.1.hostname,cfg.2.hostname,...

HardwareBenchmarker:
- sudo java -cp node-maintainer-jar-with-dependencies.jar com.yahoo.vespa.hosted.node.verification.hardware.HardwareBenchmarker cfg.1.hostname,cfg.2.hostname,...

In "verification" you can find a TODO file where we have listed things we did not have time to implement.
Both programs have README explaining closer what it does. 
