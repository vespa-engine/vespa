// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.autoscale;

import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.applications.Application;
import com.yahoo.vespa.hosted.provision.applications.Cluster;

/**
 * A cluster with its associated metrics which allows prediction about its future behavior.
 * For single-threaded, short-term usage.
 *
 * @author bratseth
 */
public class ClusterModel {

    private final Application application;
    private final Cluster cluster;
    private final NodeList nodes;
    private final MetricsDb metricsDb;
    private final NodeRepository nodeRepository;

    public ClusterModel(Application application,
                        Cluster cluster,
                        NodeList clusterNodes,
                        MetricsDb metricsDb,
                        NodeRepository nodeRepository) {
        this.application = application;
        this.cluster = cluster;
        this.nodes = clusterNodes;
        this.metricsDb = metricsDb;
        this.nodeRepository = nodeRepository;
    }

    public boolean isStable() {
        return isStable(nodes, nodeRepository);
    }

    public static boolean isStable(NodeList clusterNodes, NodeRepository nodeRepository) {
        // The cluster is processing recent changes
        if (clusterNodes.stream().anyMatch(node -> node.status().wantToRetire() ||
                                            node.allocation().get().membership().retired() ||
                                            node.allocation().get().isRemovable()))
            return false;

        // A deployment is ongoing
        if (nodeRepository.nodes().list(Node.State.reserved).owner(clusterNodes.first().get().allocation().get().owner()).size() > 0)
            return false;

        return true;
    }



}
