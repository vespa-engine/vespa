// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

/**
 * Set of names of the metrics emitted by the cluster controller.
 *
 * Note that these metric names will be implicitly prefixed with the
 * string "cluster-controller." when updated via MetricUpdater.
 *
 * @author vekterli
 */
class MetricNames {
    static final String AGREED_MASTER_VOTES = "agreed-master-votes";
    static final String AVAILABLE_NODES_RATIO = "available-nodes.ratio";
    static final String BUSY_TICK_TIME_MS = "busy-tick-time-ms";
    static final String CLUSTER_BUCKETS_OUT_OF_SYNC_RATIO = "cluster-buckets-out-of-sync-ratio";
    static final String CLUSTER_CONTROLLER = "cluster-controller";
    static final String CLUSTER_STATE_CHANGE = "cluster-state-change";
    static final String IDLE_TICK_TIME_MS = "idle-tick-time-ms";
    static final String IS_MASTER = "is-master";
    static final String NODES_NOT_CONVERGED = "nodes-not-converged";
    static final String NODE_EVENT = "node-event";
    static final String REMOTE_TASK_QUEUE_SIZE = "remote-task-queue.size";
    static final String STORED_DOCUMENT_BYTES = "stored-document-bytes";
    static final String STORED_DOCUMENT_COUNT = "stored-document-count";
    static final String WORK_MS = "work-ms";

    static class ResourceUsage {
        static final String DISK_LIMIT = "resource_usage.disk_limit";
        static final String MAX_DISK_UTILIZATION = "resource_usage.max_disk_utilization";
        static final String MAX_MEMORY_UTILIZATION = "resource_usage.max_memory_utilization";
        static final String MEMORY_LIMIT = "resource_usage.memory_limit";
        static final String NODES_ABOVE_LIMIT = "resource_usage.nodes_above_limit";
    }
}
