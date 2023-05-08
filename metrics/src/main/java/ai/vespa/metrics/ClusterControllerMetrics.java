package ai.vespa.metrics;

/**
 * @author yngve
 */
public enum ClusterControllerMetrics implements VespaMetrics {

    DOWN_COUNT("cluster-controller.down.count", Unit.NODE, "Number of content nodes down"),
    INITIALIZING_COUNT("cluster-controller.initializing.count", Unit.NODE, "Number of content nodes initializing"),
    MAINTENANCE_COUNT("cluster-controller.maintenance.count", Unit.NODE, "Number of content nodes in maintenance"),
    RETIRED_COUNT("cluster-controller.retired.count", Unit.NODE, "Number of content nodes that are retired"),
    STOPPING_COUNT("cluster-controller.stopping.count", Unit.NODE, "Number of content nodes currently stopping"),
    UP_COUNT("cluster-controller.up.count", Unit.NODE, "Number of content nodes up"),
    CLUSTER_STATE_CHANGE_COUNT("cluster-controller.cluster-state-change.count", Unit.NODE, "Number of nodes changing state"),
    BUSY_TICK_TIME_MS("cluster-controller.busy-tick-time-ms", Unit.MILLISECOND, "Time busy"),
    IDLE_TICK_TIME_MS("cluster-controller.idle-tick-time-ms", Unit.MILLISECOND, "Time idle"),
    WORK_MS("cluster-controller.work-ms", Unit.MILLISECOND, "Time used for actual work"),
    IS_MASTER("cluster-controller.is-master", Unit.BINARY, "1 if this cluster controller is currently the master, or 0 if not"),
    REMOTE_TASK_QUEUE_SIZE("cluster-controller.remote-task-queue.size", Unit.OPERATION, "Number of remote tasks queued"),
    // TODO(hakonhall): Update this name once persistent "count" metrics has been implemented.
    // DO NOT RELY ON THIS METRIC YET.
    NODE_EVENT_COUNT("cluster-controller.node-event.count", Unit.OPERATION, "Number of node events"),
    RESOURCE_USAGE_NODES_ABOVE_LIMIT("cluster-controller.resource_usage.nodes_above_limit", Unit.NODE, "The number of content nodes above resource limit, blocking feed"),
    RESOURCE_USAGE_MAX_MEMORY_UTILIZATION("cluster-controller.resource_usage.max_memory_utilization", Unit.FRACTION, "Current memory utilisation, per content node"),
    RESOURCE_USAGE_MAX_DISK_UTILIZATION("cluster-controller.resource_usage.max_disk_utilization", Unit.FRACTION, "Current disk space utilisation, per content node"),
    RESOURCE_USAGE_MEMORY_LIMIT("cluster-controller.resource_usage.memory_limit", Unit.FRACTION, "Disk space limit as a fraction of available disk space"),
    RESOURCE_USAGE_DISK_LIMIT("cluster-controller.resource_usage.disk_limit", Unit.FRACTION, "Memory space limit as a fraction of available memory"),
    REINDEXING_PROGRESS("reindexing.progress", Unit.FRACTION, "Re-indexing progress");


    private final String name;
    private final Unit unit;
    private final String description;

    ClusterControllerMetrics(String name, Unit unit, String description) {
        this.name = name;
        this.unit = unit;
        this.description = description;
    }

    public String baseName() {
        return name;
    }

    public Unit unit() {
        return unit;
    }

    public String description() {
        return description;
    }

}
