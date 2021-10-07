// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.search;

import com.yahoo.vespa.model.content.DispatchSpec;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Class used to validate that multilevel dispatch is correctly setup in an indexed content cluster.
 *
 * @author geirst
 */
public class MultilevelDispatchValidator {

    private final String clusterName;
    private final DispatchSpec dispatchSpec;
    private final List<SearchNode> searchNodes;

    public MultilevelDispatchValidator(String clusterName,
                                       DispatchSpec dispatchSpec,
                                       List<SearchNode> searchNodes) {
        this.clusterName = clusterName;
        this.dispatchSpec = dispatchSpec;
        this.searchNodes = searchNodes;
    }

    public void validate() {
        validateThatWeReferenceNodesOnlyOnce();
        validateThatWeReferenceAllNodes();
        validateThatWeUseValidNodeReferences();
    }

    private void validateThatWeReferenceNodesOnlyOnce() {
        Set<Integer> distKeys = new HashSet<>();
        for (DispatchSpec.Group group : dispatchSpec.getGroups()) {
            for (DispatchSpec.Node node : group.getNodes()) {
                int distKey = node.getDistributionKey();
                if (distKeys.contains(distKey)) {
                    throw new IllegalArgumentException(getErrorMsgPrefix() + "Expected nodes to be referenced only once in dispatch groups, but node with distribution key '" + distKey + "' is referenced multiple times.");
                }
                distKeys.add(distKey);
            }
        }
    }

    private void validateThatWeReferenceAllNodes() {
        Set<Integer> distKeys = createDistributionKeysSet();
        for (DispatchSpec.Group group : dispatchSpec.getGroups()) {
            for (DispatchSpec.Node node : group.getNodes()) {
                distKeys.remove(node.getDistributionKey());
            }
        }
        if (!distKeys.isEmpty()) {
            Object[] sorted = distKeys.toArray();
            Arrays.sort(sorted);
            throw new IllegalArgumentException(getErrorMsgPrefix() + "Expected all nodes to be referenced in dispatch groups, but " + distKeys.size() +
                    " node(s) with distribution keys " + Arrays.toString(sorted) + " are not referenced.");
        }
    }

    private void validateThatWeUseValidNodeReferences() {
        Set<Integer> distKeys = createDistributionKeysSet();
        for (DispatchSpec.Group group : dispatchSpec.getGroups()) {
            for (DispatchSpec.Node node : group.getNodes()) {
                int distKey = node.getDistributionKey();
                if (!distKeys.contains(distKey)) {
                    throw new IllegalArgumentException(getErrorMsgPrefix() + "Expected all node references in dispatch groups to reference existing nodes, " +
                            "but node with distribution key '" + distKey + "' does not exists.");
                }
            }
        }
    }

    private Set<Integer> createDistributionKeysSet() {
        Set<Integer> distKeys = new HashSet<>();
        for (SearchNode node : searchNodes) {
            distKeys.add(node.getDistributionKey());
        }
        return distKeys;
    }

    private String getErrorMsgPrefix() {
        return "In indexed content cluster '" + clusterName + "': ";
    }

}
