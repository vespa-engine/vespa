# Spec Verification
Verifies that the spec information in node repo coincides with what found on the node and reports back to node repo. 

## Code "walkthrough"
The main class SpecVerifier uses the "noderepo" package to retrieve node spec from node repo. 
It can be called with one parameter, the config server hostname, or with none (it will then use yinst to retrieve config server host name). It finds hostname using "HostURLGenerator" in the "commons" package.
It then retrieves all the hardware information at the node with the "retrievers" package and stores the values as a "HardwareInfo" object. 
SpecVerifier then uses HardwareNodeComparator to compare spec from node repo and the node itself and uses the "report" package to generate a report and to report back to node repo. 