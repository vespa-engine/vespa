// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.dns;

import ai.vespa.http.DomainName;

import java.util.Objects;

/**
 * An implementation of {@link AliasTarget} where is requests are answered based on the weight assigned to the
 * record, as a proportion of the total weight for all records having the same DNS name.
 * <p>
 * The portion of received traffic is calculated as follows: (record weight / sum of the weights of all records).
 *
 * @author mpolden
 */
public final class WeightedAliasTarget extends AliasTarget {

    static final String TARGET_TYPE = "weighted";

    private final long weight;

    public WeightedAliasTarget(DomainName name, String dnsZone, String id, long weight) {
        super(name, dnsZone, id);
        this.weight = weight;
        if (weight < 0) throw new IllegalArgumentException("Weight cannot be negative");
    }

    /** The weight of this target */
    public long weight() {
        return weight;
    }

    @Override
    public RecordData pack() {
        return RecordData.from(String.join("/", TARGET_TYPE, name().value(), dnsZone(), id(), Long.toString(weight)));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        WeightedAliasTarget that = (WeightedAliasTarget) o;
        return weight == that.weight;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), weight);
    }

    @Override
    public String toString() {
        return "weighted target for " + name() + "[id=" + id() + ",dnsZone=" + dnsZone() + ",weight=" + weight + "]";
    }

    /** Unpack weighted alias from given record data */
    public static WeightedAliasTarget unpack(RecordData data) {
        var parts = data.asString().split("/");
        if (parts.length != 5) {
            throw new IllegalArgumentException("Expected data to be on format type/name/DNS-zone/zone-id/weight, " +
                                               "but got " + data.asString());
        }
        if (!TARGET_TYPE.equals(parts[0])) {
            throw new IllegalArgumentException("Unexpected type '" + parts[0] + "'");
        }
        return new WeightedAliasTarget(DomainName.of(parts[1]), parts[2], parts[3], Long.parseLong(parts[4]));
    }

}
