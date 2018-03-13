// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core.hostinfo;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * Keeping information about a storage node seen from the distributor.
 *
 * @author Haakon Dybdahl
 */
public class StorageNode {

    static public class BucketStats {
        private final long total;
        private final long pending;

        @JsonCreator
        public BucketStats(@JsonProperty("total") Long total, @JsonProperty("pending") Long pending) {
            this.total = total;
            this.pending = pending;
        }

        public long getTotal() {
            return total;
        }
        public long getPending() {
            return pending;
        }
    }

    static public class BucketSpaceStats {
        private final String name;
        @JsonProperty("buckets")
        private BucketStats bucketStats = null;

        @JsonCreator
        public BucketSpaceStats(@JsonProperty("name") String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
        public boolean valid() {
            return bucketStats != null;
        }
        public BucketStats getBucketStats() {
            return bucketStats;
        }
    }

    private final Integer index;

    // If a Distributor does not manage any bucket copies for a particular storage node,
    // then the distributor will not return any min-current-replication-factor for that
    // storage node.
    @JsonProperty("min-current-replication-factor")
    private Integer minCurrentReplicationFactor;

    @JsonProperty("bucket-spaces")
    private List<BucketSpaceStats> bucketSpacesStats = new ArrayList<>();

    @JsonCreator
    public StorageNode(@JsonProperty("node-index") Integer index) {
        this.index = index;
    }

    public Integer getIndex() {
        return index;
    }

    // See documentation on minCurrentReplicationFactor.
    public Integer getMinCurrentReplicationFactorOrNull() {
        return minCurrentReplicationFactor;
    }

    public List<BucketSpaceStats> getBucketSpacesStats() {
        return bucketSpacesStats;
    }
}
