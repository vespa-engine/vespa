// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.dns;

import com.yahoo.config.provision.zone.ZoneId;

import java.util.Objects;

/**
 * An implementation of {@link DirectTarget} that uses latency-based routing.
 *
 * @author freva
 */
public final class LatencyDirectTarget extends DirectTarget {

    static final String TARGET_TYPE = "latency";

    private final ZoneId zone;

    public LatencyDirectTarget(RecordData recordData, ZoneId zone) {
        super(recordData, zone.value());
        this.zone = Objects.requireNonNull(zone);
    }

    /** The zone this record points to */
    public ZoneId zone() {
        return zone;
    }

    @Override
    public RecordData pack() {
        return RecordData.from(String.join("/", TARGET_TYPE, recordData().asString(), id()));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        LatencyDirectTarget that = (LatencyDirectTarget) o;
        return zone.equals(that.zone);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), zone);
    }

    @Override
    public String toString() {
        return "latency target for " + recordData() + " [id=" + id() + "]";
    }

    /** Unpack latency alias from given record data */
    public static LatencyDirectTarget unpack(RecordData data) {
        var parts = data.asString().split("/");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Expected data to be on format target-type/record-data/zone-id, but got " +
                                               data.asString());
        }
        if (!TARGET_TYPE.equals(parts[0])) {
            throw new IllegalArgumentException("Unexpected type '" + parts[0] + "'");
        }
        return new LatencyDirectTarget(RecordData.from(parts[1]), ZoneId.from(parts[2]));
    }

}
