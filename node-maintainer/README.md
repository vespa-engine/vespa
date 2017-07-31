# Node Admin Maintenance

Executes maintenance jobs, such as deleting old logs, processing and reporting coredumps, on behalf of node-admin. 
Node admin maintenance runs as a separate JVM from node-admin to make it possible to run it as root if needed.

## Node Verification
Node verification for both hardware and spec. Hardware is verified by performing different benchmarking tasks, 
while spec is verified by comparing information reported by the OS with the spec from node repository.
