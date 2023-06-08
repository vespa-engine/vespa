package ai.vespa.metrics;

/**
 * @author yngveaasheim
 */
public enum ConfigServerMetrics implements VespaMetrics {

    REQUESTS("configserver.requests", Unit.REQUEST, "Number of requests processed"),
    FAILED_REQUESTS("configserver.failedRequests", Unit.REQUEST, "Number of requests that failed"),
    LATENCY("configserver.latency", Unit.MILLISECOND, "Time to complete requests"),
    CACHE_CONFIG_ELEMS("configserver.cacheConfigElems", Unit.ITEM, "Time to complete requests"),
    CACHE_CHECKSUM_ELEMS("configserver.cacheChecksumElems", Unit.ITEM, "Number of checksum elements in the cache"),
    HOSTS("configserver.hosts", Unit.NODE, "The number of nodes being served configuration from the config server cluster"),
    TENANTS("configserver.tenants", Unit.INSTANCE, "The number of tenants being served configuration from the config server cluster"),
    APPLICATIONS("configserver.applications", Unit.INSTANCE, "The number of applications being served configuration from the config server cluster"),
    DELAYED_RESPONSES("configserver.delayedResponses", Unit.RESPONSE, "Number of delayed responses"),
    SESSION_CHANGE_ERRORS("configserver.sessionChangeErrors", Unit.SESSION, "Number of session change errors"),
    UNKNOWN_HOST_REQUEST("configserver.unknownHostRequests", Unit.REQUEST, "Config requests from unknown hosts"),
    NEW_SESSIONS("configserver.newSessions", Unit.SESSION, "New config sessions"),
    PREPARED_SESSIONS("configserver.preparedSessions", Unit.SESSION, "Prepared config sessions"),
    ACTIVE_SESSIONS("configserver.activeSessions", Unit.SESSION, "Active config sessions"),
    INACTIVE_SESSIONS("configserver.inactiveSessions", Unit.SESSION, "Inactive config sessions"),
    ADDED_SESSIONS("configserver.addedSessions", Unit.SESSION, "Added config sessions"),
    REMOVED_SESSIONS("configserver.removedSessions", Unit.SESSION, "Removed config sessions"),
    RPC_SERVER_WORK_QUEUE_SIZE("configserver.rpcServerWorkQueueSize", Unit.ITEM, "Number of elements in the RPC server work queue"),

    MAINTENANCE_DEPLOYMENT_TRANSIENT_FAILURE("maintenanceDeployment.transientFailure", Unit.OPERATION, "Number of maintenance deployments that failed with a transient failure"),
    MAINTENANCE_DEPLOYMENT_FAILURE("maintenanceDeployment.failure", Unit.OPERATION, "Number of maintenance deployments that failed with a permanent failure"),

    // ZooKeeper related metrics
    ZK_CONNECTIONS_LOST("configserver.zkConnectionLost", Unit.CONNECTION, "Number of ZooKeeper connections lost"),
    ZK_RECONNECTED("configserver.zkReconnected", Unit.CONNECTION, "Number of ZooKeeper reconnections"),
    ZK_CONNECTED("configserver.zkConnected", Unit.NODE, "Number of ZooKeeper nodes connected"),
    ZK_SUSPENDED("configserver.zkSuspended", Unit.NODE, "Number of ZooKeeper nodes suspended"),
    ZK_Z_NODES("configserver.zkZNodes", Unit.NODE, "Number of ZooKeeper nodes present"),
    ZK_AVG_LATENCY("configserver.zkAvgLatency", Unit.MILLISECOND, "Average latency for ZooKeeper requests"), // TODO: Confirm metric name
    ZK_MAX_LATENCY("configserver.zkMaxLatency", Unit.MILLISECOND, "Max latency for ZooKeeper requests"),
    ZK_CONNECTIONS("configserver.zkConnections", Unit.CONNECTION, "Number of ZooKeeper connections"),
    ZK_OUTSTANDING_REQUESTS("configserver.zkOutstandingRequests", Unit.REQUEST, "Number of ZooKeeper requests in flight"),

    // Orchestrator lock metrics
    ORCHESTRATOR_LOCK_ACQUIRE_LATENCY("orchestrator.lock.acquire-latency", Unit.SECOND, "Time to acquire zookeeper lock"),
    ORCHESTRATOR_LOCK_ACQUIRE_SUCCESS("orchestrator.lock.acquire-success", Unit.OPERATION, "Number of times zookeeper lock has been acquired successfully"),
    ORCHESTRATOR_LOCK_ACQUIRE_TIMEOUT("orchestrator.lock.acquire-timedout", Unit.OPERATION, "Number of times zookeeper lock couldn't be acquired within timeout"),
    ORCHESTRATOR_LOCK_ACQUIRE("orchestrator.lock.acquire", Unit.OPERATION, "Number of attempts to acquire zookeeper lock"),
    ORCHESTRATOR_LOCK_ACQUIRED("orchestrator.lock.acquired", Unit.OPERATION, "Number of times zookeeper lock was acquired"),
    ORCHESTRATOR_LOCK_HOLD_LATENCY("orchestrator.lock.hold-latency", Unit.SECOND, "Time zookeeper lock was held before it was released"),

    // Node repository metrics
    NODES_ACTIVE("nodes.active", Unit.NODE, "The number of active nodes in a cluster"),
    NODES_NON_ACTIVE("nodes.nonActive", Unit.NODE, "The number of non-active nodes in a cluster"),
    NODES_NON_ACTIVE_FRACTION("nodes.nonActiveFraction", Unit.NODE, "The fraction of non-active nodes vs total nodes in a cluster"),
    NODES_EXCLUSIVE_SWITCH_FRACTION("nodes.exclusiveSwitchFraction", Unit.FRACTION, "The fraction of nodes in a cluster on exclusive network switches"),

    CLUSTER_COST("cluster.cost", Unit.DOLLAR_PER_HOUR, "The cost of the nodes allocated to a certain cluster, in $/hr"),
    CLUSTER_LOAD_IDEAL_CPU("cluster.load.ideal.cpu", Unit.FRACTION, "The ideal cpu load of a certain cluster"),
    CLUSTER_LOAD_IDEAL_MEMORY("cluster.load.ideal.memory", Unit.FRACTION, "The ideal memory load of a certain cluster"),
    CLUSTER_LOAD_IDEAL_DISK("cluster.load.ideal.disk", Unit.FRACTION, "The ideal disk load of a certain cluster"),

    ZONE_WORKING("zone.working", Unit.BINARY, "The value 1 if zone is considered healthy, 0 if not. This is decided by considering the number of non-active nodes vs the number of active nodes in a zone"),
    CACHE_NODE_OBJECT_HIT_RATE("cache.nodeObject.hitRate", Unit.FRACTION, "The fraction of cache hits vs cache lookups for the node object cache"),
    CACHE_NODE_OBJECT_EVICTION_COUNT("cache.nodeObject.evictionCount", Unit.ITEM, "The number of cache elements evicted from the node object cache"),
    CACHE_NODE_OBJECT_SIZE("cache.nodeObject.size", Unit.ITEM, "The number of cache elements in the node object cache"),
    CACHE_CURATOR_HIT_RATE("cache.curator.hitRate", Unit.FRACTION, "The fraction of cache hits vs cache lookups for the curator cache"),
    CACHE_CURATOR_EVICTION_COUNT("cache.curator.evictionCount", Unit.ITEM, "The number of cache elements evicted from the curator cache"),
    CACHE_CURATOR_SIZE("cache.curator.size", Unit.ITEM, "The number of cache elements in the curator cache"),
    WANTED_RESTART_GENERATION("wantedRestartGeneration", Unit.GENERATION, "Wanted restart generation for tenant node"),
    CURRENT_RESTART_GENERATION("currentRestartGeneration", Unit.GENERATION, "Current restart generation for tenant node"),
    WANT_TO_RESTART("wantToRestart", Unit.BINARY, "One if node wants to restart, zero if not"),
    WANTED_REBOOT_GENERATION("wantedRebootGeneration", Unit.GENERATION, "Wanted reboot generation for tenant node"),
    CURRENT_REBOOT_GENERATION("currentRebootGeneration", Unit.GENERATION, "Current reboot generation for tenant node"),
    WANT_TO_REBOOT("wantToReboot", Unit.BINARY, "One if node wants to reboot, zero if not"),
    RETIRED("retired", Unit.BINARY, "One if node is retired, zero if not"),
    WANTED_VESPA_VERSION("wantedVespaVersion", Unit.VERSION, "Wanted vespa version for the node, in the form <MINOR.PATCH>. Major version is not included here"),
    CURRENT_VESPA_VERSION("currentVespaVersion", Unit.VERSION, "Current vespa version for the node, in the form <MINOR.PATCH>. Major version is not included here"),
    WANT_TO_CHANGE_VESPA_VERSION("wantToChangeVespaVersion", Unit.BINARY, "One if node want to change Vespa version, zero if not"),
    HAS_WIRE_GUARD_KEY("hasWireguardKey", Unit.BINARY, "One if node has a WireGuard key, zero if not"),
    WANT_TO_RETIRE("wantToRetire", Unit.BINARY, "One if node wants to retire, zero if not"),
    WANT_TO_DEPROVISION("wantToDeprovision", Unit.BINARY, "One if node wants to be deprovisioned, zero if not"),
    FAIL_REPORT("failReport", Unit.BINARY, "One if there is a fail report for the node, zero if not"),
    SUSPENDED("suspended", Unit.BINARY, "One if the node is suspended, zero if not"),
    SUSPENDED_SECONDS("suspendedSeconds", Unit.SECOND, "The number of seconds the node has been suspended"),
    NUMBER_OF_SERVICES_UP("numberOfServicesUp", Unit.INSTANCE, "The number of services confirmed to be running on a node"),
    NUMBER_OF_SERVICES_NOT_CHECKED("numberOfServicesNotChecked", Unit.INSTANCE, "The number of services supposed to run on a node, that has not checked"),
    NUMBER_OF_SERVICES_DOWN("numberOfServicesDown", Unit.INSTANCE, "The number of services confirmed to not be running on a node"),
    SOME_SERVICES_DOWN("someServicesDown", Unit.BINARY, "One if one or more services has been confirmed to not run on a node, zero if not"),
    NUMBER_OF_SERVICES_UNKNOWN("numberOfServicesUnknown", Unit.INSTANCE, "The number of services the config server does not know if is running on a node"),
    NODE_FAILER_BAD_NODE("nodeFailerBadNode", Unit.BINARY, "One if the node is failed due to being bad, zero if not"),
    DOWN_IN_NODE_REPO("downInNodeRepo", Unit.BINARY, "One if the node is registered as being down in the node repository, zero if not"),
    NUMBER_OF_SERVICES("numberOfServices", Unit.INSTANCE, "Number of services supposed to run on a node"),
    LOCK_ATTEMPT_ACQUIRE_MAX_ACTIVE_LATENCY("lockAttempt.acquireMaxActiveLatency", Unit.SECOND, "Maximum duration for keeping a lock, ending during the metrics snapshot, or still being kept at the end or this snapshot period"),
    LOCK_ATTEMPT_ACQUIRE_HZ("lockAttempt.acquireHz", Unit.OPERATION_PER_SECOND, "Average number of locks acquired per second the snapshot period"),
    LOCK_ATTEMPT_ACQUIRE_LOAD("lockAttempt.acquireLoad", Unit.OPERATION, "Average number of locks held concurrently during the snapshot period"),
    LOCK_ATTEMPT_LOCKED_LATENCY("lockAttempt.lockedLatency", Unit.SECOND, "Longest lock duration in the snapshot period"),
    LOCK_ATTEMPT_LOCKED_LOAD("lockAttempt.lockedLoad", Unit.OPERATION, "Average number of locks held concurrently during the snapshot period"),
    LOCK_ATTEMPT_ACQUIRE_TIMED_OUT("lockAttempt.acquireTimedOut", Unit.OPERATION, " Number of locking attempts that timed out during the snapshot period"),
    LOCK_ATTEMPT_DEADLOCK("lockAttempt.deadlock", Unit.OPERATION, "Number of lock grab deadlocks detected during the snapshont period"),
    LOCK_ATTEMPT_ERRORS("lockAttempt.errors", Unit.OPERATION, "Number of other lock related errors detected during the snapshont period"),

    HOSTED_VESPA_DOCKER_TOTAL_CAPACITY_CPU("hostedVespa.docker.totalCapacityCpu", Unit.VCPU, "Total number of VCPUs on tenant hosts managed by hosted Vespa in a zone"),
    HOSTED_VESPA_DOCKER_TOTAL_CAPACITY_MEM("hostedVespa.docker.totalCapacityMem", Unit.GIGABYTE, "Total amount of memory on tenant hosts managed by hosted Vespa in a zone"),
    HOSTED_VESPA_DOCKER_TOTAL_CAPACITY_DISK("hostedVespa.docker.totalCapacityDisk", Unit.GIGABYTE, "Total amount of disk space on tenant hosts managed by hosted Vespa in a zone"),
    HOSTED_VESPA_DOCKER_FREE_CAPACITY_CPU("hostedVespa.docker.freeCapacityCpu", Unit.VCPU, "Total number of free VCPUs on tenant hosts managed by hosted Vespa in a zone"),
    HOSTED_VESPA_DOCKER_FREE_CAPACITY_MEM("hostedVespa.docker.freeCapacityMem", Unit.GIGABYTE, "Total amount of free memory on tenant hosts managed by hosted Vespa in a zone"),
    HOSTED_VESPA_DOCKER_FREE_CAPACITY_DISK("hostedVespa.docker.freeCapacityDisk", Unit.GIGABYTE, "Total amount of free disk space on tenant hosts managed by hosted Vespa in a zone"),
    HOSTED_VESPA_DOCKER_ALLOCATED_CAPACITY_CPU("hostedVespa.docker.allocatedCapacityCpu", Unit.VCPU, "Total number of allocated VCPUs on tenant hosts managed by hosted Vespa in a zone"),
    HOSTED_VESPA_DOCKER_ALLOCATED_CAPACITY_MEM("hostedVespa.docker.allocatedCapacityMem", Unit.GIGABYTE, "Total amount of allocated memory on tenant hosts managed by hosted Vespa in a zone"),
    HOSTED_VESPA_DOCKER_ALLOCATED_CAPACITY_DISK("hostedVespa.docker.allocatedCapacityDisk", Unit.GIGABYTE, "Total amount of allocated disk space on tenant hosts managed by hosted Vespa in a zone"),
    HOSTED_VESPA_BREAKFIXED_HOSTS("hostedVespa.breakfixedHosts", Unit.HOST, "Number of hosts managed that are breakfixed in a zone"),
    HOSTED_VESPA_PENDING_REDEPLOYMENTS("hostedVespa.pendingRedeployments", Unit.TASK, "The number of hosted Vespa re-deployments pending"),
    HOSTED_VESPA_DOCKER_SKEW("hostedVespa.docker.skew", Unit.FRACTION, "A number in the range 0..1 indicating how well allocated resources are balanced with availability on hosts");

    private final String name;
    private final Unit unit;
    private final String description;

    ConfigServerMetrics(String name, Unit unit, String description) {
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
