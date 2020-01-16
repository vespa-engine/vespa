// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.content;

/**
 * Class used to validate that hierarchic distribution is correctly setup when having an indexed content cluster.
 *
 * Note that this class does not implement the com.yahoo.vespa.model.application.validation.Validator interface,
 * but is instead used in the context of com.yahoo.vespa.model.ConfigProducer.validate() such that it can be unit tested
 * without having to build the complete vespa model.
 *
 * @author geirst
 */
public class IndexedHierarchicDistributionValidator {

    private final String clusterName;
    private final StorageGroup rootGroup;
    private final Redundancy redundancy;
    private final TuningDispatch.DispatchPolicy dispatchPolicy;

    public IndexedHierarchicDistributionValidator(String clusterName,
                                                  StorageGroup rootGroup,
                                                  Redundancy redundancy,
                                                  TuningDispatch.DispatchPolicy dispatchPolicy) {
        this.clusterName = clusterName;
        this.rootGroup = rootGroup;
        this.redundancy = redundancy;
        this.dispatchPolicy = dispatchPolicy;
    }

    public void validate() {
        validateThatWeHaveOneGroupLevel();
        validateThatLeafGroupsHasEqualNumberOfNodes();
        validateThatLeafGroupsCountIsAFactorOfRedundancy(clusterName, redundancy.effectiveFinalRedundancy(), rootGroup.getSubgroups().size());
        validateThatRedundancyPerGroupIsEqual();
        validateThatReadyCopiesIsCompatibleWithRedundancy(clusterName, redundancy.effectiveFinalRedundancy(), redundancy.effectiveReadyCopies(), rootGroup.getSubgroups().size());
    }

    private void validateThatWeHaveOneGroupLevel() {
        for (StorageGroup group : rootGroup.getSubgroups()) {
            if (group.getSubgroups().size() > 0) {
                throw new IllegalArgumentException(getErrorMsgPrefix(clusterName) + "Expected all groups under root group '" +
                                                   rootGroup.getName() + "' to be leaf groups only containing nodes, but sub group '" + group.getName() + "' contains " +
                                                   group.getSubgroups().size() + " sub groups.");
            }
        }
    }

    private void validateThatLeafGroupsHasEqualNumberOfNodes() {
        if (dispatchPolicy != TuningDispatch.DispatchPolicy.ROUNDROBIN) return;

        StorageGroup previousGroup = null;
        for (StorageGroup group : rootGroup.getSubgroups()) {
            if (previousGroup == null) { // first group
                previousGroup = group;
                continue;
            }

            if (group.getNodes().size() != previousGroup.getNodes().size())
                throw new IllegalArgumentException(getErrorMsgPrefix(clusterName) + "Expected leaf groups to contain an equal number of nodes, but leaf group '" +
                        previousGroup.getName() + "' contains " + previousGroup.getNodes().size() + " node(s) while leaf group '" +
                        group.getName() + "' contains " + group.getNodes().size() + " node(s).");
            previousGroup = group;
        }
    }

    static public void validateThatLeafGroupsCountIsAFactorOfRedundancy(String clusterName, int totalRedundancy, int subGroups) {
        if (totalRedundancy % subGroups != 0) {
            throw new IllegalArgumentException(getErrorMsgPrefix(clusterName) + "Expected number of leaf groups (" +
                                               subGroups + ") to be a factor of redundancy (" +
                                               totalRedundancy + "), but it is not.");
        }
    }

    private void validateThatRedundancyPerGroupIsEqual() {
        int redundancyPerGroup = redundancy.effectiveFinalRedundancy() / rootGroup.getSubgroups().size();
        String expPartitions = createDistributionPartitions(redundancyPerGroup, rootGroup.getSubgroups().size());
        if (!rootGroup.getPartitions().get().equals(expPartitions)) {
            throw new IllegalArgumentException(getErrorMsgPrefix(clusterName) + "Expected redundancy per leaf group to be " +
                                               redundancyPerGroup + ", but it is not according to distribution partitions '" +
                                               rootGroup.getPartitions().get() + "'. Expected distribution partitions should be '" + expPartitions + "'.");
        }
    }

    private String createDistributionPartitions(int redundancyPerGroup, int numGroups) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < numGroups - 1; ++i) {
            sb.append(redundancyPerGroup);
            sb.append("|");
        }
        sb.append("*");
        return sb.toString();
    }

    static public void validateThatReadyCopiesIsCompatibleWithRedundancy(String clusterName, int totalRedundancy, int totalReadyCopies, int groupCount) {
        if (totalRedundancy % groupCount != 0) {
            throw new IllegalArgumentException(getErrorMsgPrefix(clusterName) + "Expected equal redundancy per group.");
        }
        if (totalReadyCopies % groupCount != 0) {
            throw new IllegalArgumentException(getErrorMsgPrefix(clusterName) + "Expected equal amount of ready copies per group, but " +
                                totalReadyCopies + " ready copies is specified with " + groupCount + " groups");
        }
        if (totalReadyCopies == 0) {
            System.err.println(getErrorMsgPrefix(clusterName) + "Warning. No ready copies configured. At least one is recommended.");
        }
    }

    static private String getErrorMsgPrefix(String clusterName) {
        return "In indexed content cluster '" + clusterName + "' using hierarchic distribution: ";
    }
}
