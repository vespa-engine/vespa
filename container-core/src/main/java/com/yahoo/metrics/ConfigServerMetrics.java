package com.yahoo.metrics;

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

    // ZooKeeper related metrics
    ZK_CONNECTIONS_LOST("configserver.zkConnectionLost", Unit.CONNECTION, "Number of ZooKeeper connections lost"),
    ZK_RECONNECTED("configserver.zkReconnected", Unit.CONNECTION, "Number of ZooKeeper reconnections"),
    ZK_CONNECTED("configserver.zkConnected", Unit.NODE, "Number of ZooKeeper nodes connected"),
    ZK_SUSPENDED("configserver.zkSuspended", Unit.NODE, "Number of ZooKeeper nodes suspended"),
    ZK_Z_NODES("configserver.zkZNodes", Unit.NODE, "Number of ZooKeeper nodes present"),
    ZK_AVG_LATENCY("configserver.zkAvgLatency", Unit.MILLISECOND, "Average latency for ZooKeeper requests"), // TODO: Confirm metric name
    ZK_MAX_LATENCY("configserver.zkMaxLatency", Unit.MILLISECOND, "Max latency for ZooKeeper requests"),
    ZK_CONNECTIONS("configserver.zkConnections", Unit.CONNECTION, "Number of ZooKeeper connections"),
    ZK_OUTSTANDING_REQUESTS("configserver.zkOutstandingRequests", Unit.REQUEST, "Number of ZooKeeper requests in flight");

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
