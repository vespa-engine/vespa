// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.content;

import java.util.List;
import java.util.stream.Collectors;

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

    public void validate() throws Exception {
        validateThatWeHaveOneGroupLevel();
        validateThatLeafGroupsHasEqualNumberOfNodes();
        validateThatLeafGroupsCountIsAFactorOfRedundancy();
        validateThatRedundancyPerGroupIsEqual();
        validateThatReadyCopiesIsCompatibleWithRedundancy(rootGroup.getSubgroups().size());
    }

    private void validateThatWeHaveOneGroupLevel() {
        for (StorageGroup group : rootGroup.getSubgroups()) {
            if (group.getSubgroups().size() > 0) {
                throw new IllegalArgumentException(getErrorMsgPrefix() + "Expected all groups under root group '" +
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
                throw new IllegalArgumentException(getErrorMsgPrefix() + "Expected leaf groups to contain an equal number of nodes, but leaf group '" +
                        previousGroup.getName() + "' contains " + previousGroup.getNodes().size() + " node(s) while leaf group '" +
                        group.getName() + "' contains " + group.getNodes().size() + " node(s).");
            previousGroup = group;
        }
    }

    private void validateThatLeafGroupsCountIsAFactorOfRedundancy() {
        if (redundancy.effectiveFinalRedundancy() % rootGroup.getSubgroups().size() != 0) {
            throw new IllegalArgumentException(getErrorMsgPrefix() + "Expected number of leaf groups (" +
                                               rootGroup.getSubgroups().size() + ") to be a factor of redundancy (" +
                                               redundancy.effectiveFinalRedundancy() + "), but it is not.");
        }
    }

    private void validateThatRedundancyPerGroupIsEqual() {
        int redundancyPerGroup = redundancy.effectiveFinalRedundancy() / rootGroup.getSubgroups().size();
        String expPartitions = createDistributionPartitions(redundancyPerGroup, rootGroup.getSubgroups().size());
        if (!rootGroup.getPartitions().get().equals(expPartitions)) {
            throw new IllegalArgumentException(getErrorMsgPrefix() + "Expected redundancy per leaf group to be " +
                                               redundancyPerGroup + ", but it is not according to distribution partitions '" +
                                               rootGroup.getPartitions().get() + "'. Expected distribution partitions should be '" + expPartitions + "'.");
        }
    }

    private List<StorageNode> nonRetired(List<StorageNode> nodes) {
        return nodes.stream().filter((node) -> { return !node.isRetired(); }  ).collect(Collectors.toList());
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

    private void validateThatReadyCopiesIsCompatibleWithRedundancy(int groupCount) throws Exception {
        if (redundancy.effectiveFinalRedundancy() % groupCount != 0) {
            throw new Exception(getErrorMsgPrefix() + "Expected equal redundancy per group.");
        }
        if (redundancy.effectiveReadyCopies() % groupCount != 0) {
            throw new Exception(getErrorMsgPrefix() + "Expected equal amount of ready copies per group, but " +
                                redundancy.effectiveReadyCopies() + " ready copies is specified with " + groupCount + " groups");
        }
        if (redundancy.effectiveReadyCopies() == 0) {
            System.err.println(getErrorMsgPrefix() + "Warning. No ready copies configured. At least one is recommended.");
        }
    }

    private String getErrorMsgPrefix() {
        return "In indexed content cluster '" + clusterName + "' using hierarchic distribution: ";
    }
}
