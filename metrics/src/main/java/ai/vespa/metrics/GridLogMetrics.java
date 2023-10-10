// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metrics;

/**
 * @author yngveaasheim
 */
public enum GridLogMetrics implements VespaMetrics {

    RECEIVED("gridlog.received", Unit.ITEM, "Entries requested to send"),
    SENT("gridlog.sent", Unit.ITEM, "Entries successfully sent"),
    NOT_SENT("gridlog.not_sent", Unit.ITEM, "Entries not sent, due to error or pruned by sampling"),
    REJECTED("gridlog.rejected", Unit.ITEM, "Entries not sent due to send queue being full"),
    SIZE("gridlog.size", Unit.BYTE, "Size of sent entries"),
    PAYLOAD_TIME("gridlog.payload_time", Unit.SECOND, "Time spent building payload"),
    BUILD_TIME("gridlog.build_time", Unit.SECOND, "Time spent building entries"),
    SEND_TIME("gridlog.send_time", Unit.SECOND, "Total time spend in worker thread");

    private final String name;
    private final Unit unit;
    private final String description;

    GridLogMetrics(String name, Unit unit, String description) {
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
