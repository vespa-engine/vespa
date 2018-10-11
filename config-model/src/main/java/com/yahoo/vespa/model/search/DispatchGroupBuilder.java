// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.search;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.vespa.model.HostResource;
import com.yahoo.vespa.model.SimpleConfigProducer;
import com.yahoo.vespa.model.content.DispatchSpec;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Class used to build the mid-level dispatch groups in an indexed content cluster.
 *
 * @author geirst
 */
public class DispatchGroupBuilder {

    private final SimpleConfigProducer dispatchParent;
    private final DispatchGroup rootDispatch;
    private final IndexedSearchCluster searchCluster;

    public DispatchGroupBuilder(SimpleConfigProducer dispatchParent,
                                DispatchGroup rootDispatch,
                                IndexedSearchCluster searchCluster) {
        this.dispatchParent = dispatchParent;
        this.rootDispatch = rootDispatch;
        this.searchCluster = searchCluster;
    }

    public void build(DeployLogger deployLogger, List<DispatchSpec.Group> groupsSpec, List<SearchNode> searchNodes) {
        Map<Integer, SearchNode> searchNodeMap = buildSearchNodeMap(searchNodes);
        for (int partId = 0; partId < groupsSpec.size(); ++partId) {
            DispatchSpec.Group groupSpec = groupsSpec.get(partId);
            DispatchGroup group = new DispatchGroup(searchCluster);
            populateDispatchGroup(deployLogger, group, groupSpec.getNodes(), searchNodeMap, partId);
        }
    }

    private void populateDispatchGroup(DeployLogger deployLogger,
                                       DispatchGroup group,
                                       List<DispatchSpec.Node> nodeList,
                                       Map<Integer, SearchNode> searchNodesMap,
                                       int partId) {
        for (int rowId = 0; rowId < nodeList.size(); ++rowId) {
            int distributionKey = nodeList.get(rowId).getDistributionKey();
            SearchNode searchNode = searchNodesMap.get(distributionKey);
            Dispatch dispatch = buildDispatch(deployLogger, group, new NodeSpec(rowId, partId), distributionKey, searchNode.getHostResource());
            group.addDispatcher(dispatch);
            rootDispatch.addSearcher(dispatch);

            // Note: the rowId in this context will be the partId for the underlying search node.
            group.addSearcher(buildSearchInterface(searchNode, rowId));
        }
    }

    /**
     * Builds a mid-level dispatcher with a configId containing the same stable distribution-key as the search node it
     * is located on.
     *
     * If this.dispatchParent has subConfigId 'dispatchers', the config ids of the mid-level
     * dispatchers are '../dispatchers/dispatch.X' where X is the distribution-key of the search node.
     *
     * The dispatch group that will contain this mid-level dispatcher is no longer part of the config producer tree,
     * but only contains information about the dispatchers and searchers in this group.
     */
    private Dispatch buildDispatch(DeployLogger deployLogger, DispatchGroup group, NodeSpec nodeSpec, int distributionKey, HostResource hostResource) {
        Dispatch dispatch = Dispatch.createDispatchWithStableConfigId(group, dispatchParent, nodeSpec, distributionKey, 1);
        dispatch.setHostResource(hostResource);
        dispatch.initService(deployLogger);
        return dispatch;
    }

    private static SearchInterface buildSearchInterface(SearchNode searchNode, int partId) {
        searchNode.updatePartition(partId); // ensure that search node uses the same partId as dispatch sees
        return new SearchNodeWrapper(new NodeSpec(0, partId), searchNode);
    }

    private static Map<Integer, SearchNode> buildSearchNodeMap(List<SearchNode> searchNodes) {
        Map<Integer, SearchNode> retval = new LinkedHashMap<>();
        for (SearchNode node : searchNodes) {
            retval.put(node.getDistributionKey(), node);
        }
        return retval;
    }

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

