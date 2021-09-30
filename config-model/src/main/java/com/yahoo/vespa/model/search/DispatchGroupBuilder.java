// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.search;

import com.yahoo.vespa.model.content.DispatchSpec;

import java.util.ArrayList;
import java.util.List;

/**
 * Class used to build the mid-level dispatch groups in an indexed content cluster.
 *
 * @author geirst
 */
public class DispatchGroupBuilder {

    public static List<DispatchSpec.Group> createDispatchGroups(List<SearchNode> searchNodes,
                                                                int numDispatchGroups) {
        if (numDispatchGroups > searchNodes.size())
            numDispatchGroups = searchNodes.size();

        List<DispatchSpec.Group> groupsSpec = new ArrayList<>();
        int numNodesPerGroup = searchNodes.size() / numDispatchGroups;
        if (searchNodes.size() % numDispatchGroups != 0) {
            numNodesPerGroup += 1;
        }
        int searchNodeIdx = 0;
        for (int i = 0; i < numDispatchGroups; ++i) {
            DispatchSpec.Group groupSpec = new DispatchSpec.Group();
            for (int j = 0; j < numNodesPerGroup && searchNodeIdx < searchNodes.size(); ++j) {
                groupSpec.addNode(new DispatchSpec.Node(searchNodes.get(searchNodeIdx++).getDistributionKey()));
            }
            groupsSpec.add(groupSpec);
        }
        assert(searchNodeIdx == searchNodes.size());
        return groupsSpec;
    }

}

