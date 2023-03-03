package com.yahoo.metrics;

/**
 * @author yngveaasheim
 */
public enum NodeAdminMetrics implements VespaMetrics {

    WORKER_CONNECTIONS("worker.connections", Unit.CONNECTION, "Yahoo! Internal: Number of connections for the routing worker having most connections per node"),  // Hosted Vespa only (routing layer) TODO: Move to a better place
    ENDPOINT_CERTIFICATE_EXPIRY_SECONDS("endpoint.certificate.expiry.seconds", Unit.SECOND, "Time until node endpoint certificate expires"),
    NODE_CERTIFICATE_EXPIRY_SECONDS("node-certificate.expiry.seconds", Unit.SECOND, "Time until node certificate expires");


    private final String name;
    private final Unit unit;
    private final String description;

    NodeAdminMetrics(String name, Unit unit, String description) {
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

