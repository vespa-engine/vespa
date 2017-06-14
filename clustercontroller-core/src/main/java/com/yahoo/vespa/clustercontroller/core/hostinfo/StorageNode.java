// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core.hostinfo;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Keeping information about a storage node seen from the distributor.
 * @author dybis
 */
public class StorageNode {

    static public class Put {
        private final Long latencyMsSum;
        private final Long count;

        @JsonCreator
        public Put(@JsonProperty("latency-ms-sum") Long latencyMsSum, @JsonProperty("count") Long count) {
            this.latencyMsSum = latencyMsSum;
            this.count = count;
        }

        public Long getLatencyMsSum() { return latencyMsSum; }
        public Long getCount() { return count; }
    }

    static public class OpsLatency {
        private final Put put;

        @JsonCreator
        public OpsLatency(@JsonProperty("put") Put put) {
            this.put = put;
        }

        public Put getPut() { return put; }
    }

    static public class Buckets {
        private final long buckets;

        @JsonCreator
        public Buckets(@JsonProperty("buckets") Long buckets) {
            this.buckets = buckets;
        }

        public long getBuckets() { return buckets; }
    }

    static public class OutstandingMergeOps {
        @JsonProperty("syncing")
        private Buckets syncing;
        @JsonProperty("copying-in")
        private Buckets copyingIn;
        @JsonProperty("moving-out")
        private Buckets movingOut;
        @JsonProperty("copying-out")
        private Buckets copyingOut;

        public Buckets getSyncingOrNull() { return syncing; }
        public Buckets getCopyingInOrNull() { return copyingIn; }
        public Buckets getMovingOutOrNull() { return movingOut; }
        public Buckets getCopyingOutOrNull() { return copyingOut; }
    }

    private final Integer index;

    @JsonProperty("ops-latency")
    private OpsLatency opsLatencies;

    // If a Distributor does not manage any bucket copies for a particular storage node,
    // then the distributor will not return any min-current-replication-factor for that
    // storage node.
    @JsonProperty("min-current-replication-factor")
    private Integer minCurrentReplicationFactor;

    @JsonProperty("outstanding-merge-ops")
    private OutstandingMergeOps outstandingMergeOps;

    @JsonCreator
    public StorageNode(@JsonProperty("node-index") Integer index) {
        this.index = index;
    }

    public Integer getIndex() {
        return index;
    }

    public OpsLatency getOpsLatenciesOrNull() {
        return opsLatencies;
    }

    // See documentation on minCurrentReplicationFactor.
    public Integer getMinCurrentReplicationFactorOrNull() {
        return minCurrentReplicationFactor;
    }

    public OutstandingMergeOps getOutstandingMergeOpsOrNull() {
        return outstandingMergeOps;
    }
}
