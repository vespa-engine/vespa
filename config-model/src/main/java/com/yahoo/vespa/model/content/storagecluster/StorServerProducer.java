// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.content.storagecluster;

import com.yahoo.config.model.api.ModelContext;
import com.yahoo.vespa.config.content.core.StorServerConfig;
import com.yahoo.vespa.model.content.cluster.ContentCluster;
import com.yahoo.vespa.model.builder.xml.dom.ModelElement;

import java.util.Optional;

/**
 * Serves config for stor-server for storage clusters (clusters of storage nodes).
 */
public class StorServerProducer implements StorServerConfig.Producer {
    public static class Builder {
        StorServerProducer build(ModelContext.Properties properties, ModelElement element) {
            ModelElement tuning = element.child("tuning");

            StorServerProducer producer = new StorServerProducer(ContentCluster.getClusterId(element), properties.featureFlags());
            if (tuning == null) return producer;

            ModelElement merges = tuning.child("merges");
            if (merges == null) return producer;

            producer.setMaxMergesPerNode(merges.integerAttribute("max-per-node"))
                    .setMaxQueueSize(merges.integerAttribute("max-queue-size"));
            return producer;
        }
    }

    private String clusterName;
    private Integer maxMergesPerNode;
    private Integer queueSize;
    private Integer bucketDBStripeBits;
    private Boolean ignoreMergeQueueLimit;

    private StorServerProducer setMaxMergesPerNode(Integer value) {
        if (value != null) {
            maxMergesPerNode = value;
        }
        return this;
    }
    private StorServerProducer setMaxQueueSize(Integer value) {
        if (value != null) {
            queueSize = value;
        }
        return this;
    }
    private StorServerProducer setBucketDBStripeBits(Integer value) {
        bucketDBStripeBits = value;
        return this;
    }

    StorServerProducer(String clusterName, ModelContext.FeatureFlags featureFlags) {
        this.clusterName = clusterName;
        maxMergesPerNode = featureFlags.maxConcurrentMergesPerNode();
        queueSize = featureFlags.maxMergeQueueSize();
        ignoreMergeQueueLimit = featureFlags.ignoreMergeQueueLimit();
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
        if (bucketDBStripeBits != null) {
            builder.content_node_bucket_db_stripe_bits(bucketDBStripeBits);
        }
        if (ignoreMergeQueueLimit != null) {
            builder.disable_queue_limits_for_chained_merges(ignoreMergeQueueLimit);
        }
    }
}
