package ai.vespa.metrics;

/**
 * @author yngve
 */
public enum SlobrokMetrics implements VespaMetrics {

    SLOBROK_HEARTBEATS_FAILED("slobrok.heartbeats.failed", Unit.REQUEST, "Number of heartbeat requests failed"),
    SLOBROK_REQUESTS_REGISTER("slobrok.requests.register", Unit.REQUEST, "Number of register requests received"),
    SLOBROK_REQUESTS_MIRROR("slobrok.requests.mirror", Unit.REQUEST, "Number of mirroring requests received"),
    SLOBROK_REQUESTS_ADMIN("slobrok.requests.admin", Unit.REQUEST, "Number of administrative requests received"),
    SLOBROK_MISSING_CONSENSUS("slobrok.missing.consensus", Unit.SECOND, "Number of seconds without full consensus with all other brokers");


    private final String name;
    private final Unit unit;
    private final String description;

    SlobrokMetrics(String name, Unit unit, String description) {
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
