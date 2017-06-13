// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.content;

import com.yahoo.vespa.config.content.core.StorDistributormanagerConfig;
import com.yahoo.vespa.model.builder.xml.dom.ModelElement;
import com.yahoo.vespa.model.content.cluster.ContentCluster;

/**
 * Represents configuration for bucket splitting.
 */
public class BucketSplitting implements StorDistributormanagerConfig.Producer {
    Integer maxDocuments;
    Integer splitSize;
    Integer minSplitCount;
    boolean useInlineBucketSplitting;

    public static class Builder {
        public BucketSplitting build(ContentCluster cluster, ModelElement clusterElem) {
            ModelElement tuning = clusterElem.getChild("tuning");
            if (tuning == null) {
                return new BucketSplitting(cluster.isMemfilePersistence(), null, null, null);
            }

            ModelElement bucketSplitting = tuning.getChild("bucket-splitting");
            if (bucketSplitting != null) {
                Integer maxDocuments = bucketSplitting.getIntegerAttribute("max-documents");
                Integer splitSize = bucketSplitting.getIntegerAttribute("max-size");
                Integer minSplitCount = bucketSplitting.getIntegerAttribute("minimum-bits");

                return new BucketSplitting(cluster.isMemfilePersistence(), maxDocuments, splitSize, minSplitCount);
            }

            return new BucketSplitting(cluster.isMemfilePersistence(), null, null, null);
        }
    }

    public BucketSplitting(boolean useInlineBucketSplitting, Integer maxDocuments, Integer splitSize, Integer minSplitCount) {
        this.maxDocuments = maxDocuments;
        this.splitSize = splitSize;
        this.minSplitCount = minSplitCount;
        this.useInlineBucketSplitting = useInlineBucketSplitting;
    }

    @Override
    public void getConfig(StorDistributormanagerConfig.Builder builder) {
        if (maxDocuments != null) {
            builder.splitcount(maxDocuments);
            builder.joincount(maxDocuments / 2);
        }
        if (splitSize != null) {
            builder.splitsize(splitSize);
            builder.joinsize(splitSize / 2);
        }
        if (minSplitCount != null) {
            builder.minsplitcount(minSplitCount);
        }

        builder.inlinebucketsplitting(useInlineBucketSplitting);
    }
}
