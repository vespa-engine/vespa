// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.content.storagecluster;

import com.yahoo.vespa.config.content.core.StorServerConfig;
import com.yahoo.vespa.model.content.cluster.ContentCluster;
import com.yahoo.vespa.model.builder.xml.dom.ModelElement;

/**
 * Serves config for stor-server for storage clusters (clusters of storage nodes).
 */
public class StorServerProducer implements StorServerConfig.Producer {
    public static class Builder {
        StorServerProducer build(ModelElement element) {
            ModelElement tuning = element.child("tuning");

            if (tuning == null) {
                return new StorServerProducer(ContentCluster.getClusterName(element), null, null);
            }

            ModelElement merges = tuning.child("merges");
            if (merges == null) {
                return new StorServerProducer(ContentCluster.getClusterName(element), null, null);
            }

            return new StorServerProducer(ContentCluster.getClusterName(element),
                    merges.integerAttribute("max-per-node"),
                    merges.integerAttribute("max-queue-size"));
        }
    }

    private String clusterName;
    private Integer maxMergesPerNode;
    private Integer queueSize;

    public StorServerProducer(String clusterName, Integer maxMergesPerNode, Integer queueSize) {
        this.clusterName = clusterName;
        this.maxMergesPerNode = maxMergesPerNode;
        this.queueSize = queueSize;
    }

    @Override
    public void getConfig(StorServerConfig.Builder builder) {
        builder.root_folder("");
        builder.is_distributor(false);

        if (clusterName != null) {
            builder.cluster_name(clusterName);
        }
        if (maxMergesPerNode != null) {
            builder.max_merges_per_node(maxMergesPerNode);
        }
        if (queueSize != null) {
            builder.max_merge_queue_size(queueSize);
        }
    }
}
