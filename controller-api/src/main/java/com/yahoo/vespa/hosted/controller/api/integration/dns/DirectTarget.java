// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.dns;

import java.util.Objects;

/**
 * Same as {@link AliasTarget}, except for targets outside AWS (cannot be targeted with ALIAS record).
 *
 * @author freva
 */
public sealed abstract class DirectTarget permits LatencyDirectTarget, WeightedDirectTarget {

    private final RecordData recordData;
    private final String id;

    protected DirectTarget(RecordData recordData, String id) {
        this.recordData = Objects.requireNonNull(recordData, "recordData must be non-null");
        this.id = Objects.requireNonNull(id, "id must be non-null");
    }

    /** A unique identifier of this record within the record group */
    public String id() {
        return id;
    }

    /** Data in this, e.g. IP address for records of type A */
    public RecordData recordData() {
        return recordData;
    }

    /** Returns the fields in this encoded as record data */
    public abstract RecordData pack();

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DirectTarget that = (DirectTarget) o;
        return recordData.equals(that.recordData) && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(recordData, id);
    }

    /** Unpack target from given record data */
    public static DirectTarget unpack(RecordData data) {
        String[] parts = data.asString().split("/");
        return switch (parts[0]) {
            case LatencyDirectTarget.TARGET_TYPE -> LatencyDirectTarget.unpack(data);
            case WeightedDirectTarget.TARGET_TYPE -> WeightedDirectTarget.unpack(data);
            default -> throw new IllegalArgumentException("Unknown alias type '" + parts[0] + "'");
        };
    }

}
