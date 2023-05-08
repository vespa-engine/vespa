package ai.vespa.metrics;

/**
 * @author yngve
 */
public enum SentinelMetrics implements VespaMetrics {

    SENTINEL_RESTARTS("sentinel.restarts", Unit.RESTART, "Number of service restarts done by the sentinel"),
    SENTINEL_TOTAL_RESTARTS("sentinel.totalRestarts", Unit.RESTART, "Total number of service restarts done by the sentinel since the sentinel was started"),
    SENTINEL_UPTIME("sentinel.uptime", Unit.SECOND, "Time  the sentinel has been running"),
    SENTINEL_RUNNING("sentinel.running", Unit.INSTANCE, "Number of services the sentinel has running currently");


    private final String name;
    private final Unit unit;
    private final String description;

    SentinelMetrics(String name, Unit unit, String description) {
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
