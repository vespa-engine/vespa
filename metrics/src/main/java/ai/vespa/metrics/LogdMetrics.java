package ai.vespa.metrics;

/**
 * @author yngveaasheim
 */
public enum LogdMetrics implements VespaMetrics {

    LOGD_PROCESSED_LINES("logd.processed.lines", Unit.ITEM, "Number of log lines processed");

    private final String name;
    private final Unit unit;
    private final String description;

    LogdMetrics(String name, Unit unit, String description) {
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

