# Node Admin Maintenance

Executes maintenance jobs, such as deleting old logs, processing and reporting coredumps, on behalf of node-admin. 
Node admin maintenance runs as a separate JVM from node-admin to make it possible to run it as root if needed.
