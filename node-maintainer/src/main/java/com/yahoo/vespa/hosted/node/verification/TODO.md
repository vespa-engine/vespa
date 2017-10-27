<!-- Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root. -->
#TODO
Here we have listed things we did not have time to do during the summer, but that we think can be implemented later.

##Spec
- The net interface speed is at the moment approved if it is over 1 000MB/s. Since some nodes are supposed to have
10 000MB/s a feature could be to either add information about interface speed in node repo or use the flavor to decide
if the interface speed is correct or not.
- In HardwareNodeComparator the spec found in node repo and on the node are compared. We set a threshold on 5%, meaning
if a the value from the node is more than 5% away from what is said in node repo (+-), then we say it is a bad value.
If this threshold is too high or too low, it has to be changed in this class.

##Benchmark
- BenchmarkResultInspector is the class that decides whether a benchmark result is ok or if the result should be
 reported. The values that decides this are not given very much thought and it could be an idea to check these.
- Benchmark is not running on docker hosts since there is not yet found a solution to only start benchmarking at reboot.
Spec verification runs every hour on node-admin, but since node-admin reboots too often, benchmarking is at the moment
not running here.

##Reporting / Node repo
- Since HardwareDivergenceReport is printed as a json string and then reported to noderepo as a string, a feature could
be to not store HardwareDivergence as a string in node repo, as it is now, but as a json. This will be mostly changes 
outside of this code, but then the member variable HardwareDivergence in NodeRepoJsonModel have to be change from string
to HardwareDivergenceReport which will cause some work in the Reporter class.
- No actions are now taken if there is anything wrong with the node. The information is only uploaded to node repo.
Here it is room for improvements and some possible solutions are:
    - Continue only reporting to node repo, but have a program that scans through all nodes and makes a list of those
    with errors in HardwareDivergence.
    - In addition to uploading the report to node repo, have actions based on what kind of errors and what kind of state
    the node has.
    - Automatically create Jira tickets when something is reported wrong on a node.
