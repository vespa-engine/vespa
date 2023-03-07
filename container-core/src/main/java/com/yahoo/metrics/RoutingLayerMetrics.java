package com.yahoo.metrics;

/**
 * @author yngveaasheim
 */

// Internal hosted Vespa only TODO: Move to a better place
public enum RoutingLayerMetrics implements VespaMetrics {

    WORKER_CONNECTIONS("worker.connections", Unit.CONNECTION, "Yahoo! Internal: Number of connections for the routing worker having most connections per node");

    private final String name;
    private final Unit unit;
    private final String description;

    RoutingLayerMetrics(String name, Unit unit, String description) {
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
