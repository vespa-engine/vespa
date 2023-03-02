package com.yahoo.metrics;

/**
 * @author yngveaasheim
 */
public enum ConfigServerMetrics implements VespaMetrics {

    CONFIGSERVER_REQUESTS("configserver.requests", Unit.REQUEST, "Number of requests processed"),
    CONFIGSERVER_FAILED_REQUESTS("configserver.failedRequests", Unit.REQUEST, "Number of requests that failed"),
    CONFIGSERVER_LATENCY("configserver.latency", Unit.MILLISECOND, "Time to complete requests"),
    CONFIGSERVER_CACHE_CONFIG_ELEMS("configserver.cacheConfigElems", Unit.ITEM, "Time to complete requests"),
    CONFIGSERVER_CACHE_CHECKSUM_ELEMS("", Unit.ITEM, "Number of checksum elements in the cache"),
    CONFIGSERVER_HOSTS("configserver.hosts", Unit.NODE, "The number of nodes being served configuration from the config server cluster"),
    CONFIGSERVER_TENANTS("configserver.tenants", Unit.INSTANCE, "The number of tenants being served configuration from the config server cluster"),
    CONFIGSERVER_APPLICATIONS("configserver.applications", Unit.INSTANCE, "The number of applications being served configuration from the config server cluster"),
    CONFIGSERVER_DELAYED_RESPONSES("configserver.delayedResponses", Unit.RESPONSE, "Number of delayed responses"),
    CONFIGSERVER_SESSION_CHANGE_ERRORS("configserver.sessionChangeErrors", Unit.SESSION, "Number of session change errors"),
    CONFIGSERVER_UNKNOWN_HOST_REQUEST("configserver.unknownHostRequests", Unit.REQUEST, "Config requests from unknown hosts"),
    CONFIGSERVER_NEW_SESSIONS("configserver.newSessions", Unit.SESSION, "New config sessions"),
    CONFIGSERVER_PREPARED_SESSIONS("configserver.preparedSessions", Unit.SESSION, "Prepared config sessions"),
    CONFIGSERVER_ACTIVE_SESSIONS("configserver.activeSessions", Unit.SESSION, "Active config sessions"),
    CONFIGSERVER_INACTIVE_SESSIONS("configserver.inactiveSessions", Unit.SESSION, "Inactive config sessions"),
    CONFIGSERVER_ADDED_SESSIONS("configserver.addedSessions", Unit.SESSION, "Added config sessions"),
    CONFIGSERVER_REMOVED_SESSIONS("configserver.removedSessions", Unit.SESSION, "Removed config sessions"),
    CONFIGSERVER_RPC_SERVER_WORK_QUEUE_SIZE("configserver.rpcServerWorkQueueSize", Unit.ITEM, "Number of elements in the RPC server work queue"),

    // ZooKeeper related metrics
    CONFIGSERVER_ZK_CONNECTIONS_LOST("configserver.zkConnectionLost", Unit.CONNECTION, "Number of ZooKeeper connections lost"),
    CONFIGSERVER_ZK_RECONNECTED("configserver.zkReconnected", Unit.CONNECTION, "Number of ZooKeeper reconnections"),
    CONFIGSERVER_ZK_CONNECTED("configserver.zkConnected", Unit.NODE, "Number of ZooKeeper nodes connected"),
    CONFIGSERVER_ZK_SUSPENDED("configserver.zkSuspended", Unit.NODE, "Number of ZooKeeper nodes suspended"),
    CONFIGSERVER_ZK_Z_NODES("configserver.zkZNodes", Unit.NODE, "Number of ZooKeeper nodes present"),
    CONFIGSERVER_ZK_AVG_LATENCY("configserver.zkAvgLatency", Unit.MILLISECOND, "Average latency for ZooKeeper requests"), // TODO: Confirm metric name
    CONFIGSERVER_ZK_MAX_LATENCY("configserver.zkMaxLatency", Unit.MILLISECOND, "Max latency for ZooKeeper requests"),
    CONFIGSERVER_ZK_CONNECTIONS("configserver.zkConnections", Unit.CONNECTION, "Number of ZooKeeper connections"),
    CONFIGSERVER_ZK_OUTSTANDING_REQUESTS("configserver.zkOutstandingRequests", Unit.REQUEST, "Number of ZooKeeper requests in flight");

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