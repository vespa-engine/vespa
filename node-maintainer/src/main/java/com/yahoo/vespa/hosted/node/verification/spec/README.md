<!-- Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root. -->
# Spec Verification
Verifies that the spec information in node repo coincides with what found on the node and reports back to node repo. 

## Code "walkthrough"
The main class SpecVerifier uses the "noderepo" package in "commons" to retrieve node spec from node repo. 

It must be called with one parameter, the config server hostname. It finds hostname using "HostURLGenerator" in the "commons" package.

It then retrieves all the hardware information at the node with the "retrievers" package and stores the values as a 
"HardwareInfo" object. 

SpecVerifier then uses HardwareNodeComparator to compare spec from node repo and the node itself. It generates a 
SpecVerificationReport and uses Reporter in "commons" to retrieve the old HardwareDivergence report from node repo and update it. Reporter then prints the new
updated HardwareDivergence report such that it can be updated in node repo by chef or other.
