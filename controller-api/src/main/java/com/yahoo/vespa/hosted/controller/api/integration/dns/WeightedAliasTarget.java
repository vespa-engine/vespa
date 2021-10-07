// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.dns;

import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.zone.ZoneId;

import java.util.Objects;

/**
 * An implementation of {@link AliasTarget} where is requests are answered based on the weight assigned to the
 * record, as a proportion of the total weight for all records having the same DNS name.
 *
 * The portion of received traffic is calculated as follows: (record weight / sum of the weights of all records).
 *
 * @author mpolden
 */
public class WeightedAliasTarget extends AliasTarget {

    private final long weight;

    public WeightedAliasTarget(HostName name, String dnsZone, ZoneId zone, long weight) {
        super(name, dnsZone, zone.value());
        this.weight = weight;
        if (weight < 0) throw new IllegalArgumentException("Weight cannot be negative");
    }

    /** The weight of this target */
    public long weight() {
        return weight;
    }

    @Override
    public RecordData pack() {
        return RecordData.from("weighted/" + name().value() + "/" + dnsZone() + "/" + id() + "/" + weight);
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
        if (!"weighted".equals(parts[0])) {
            throw new IllegalArgumentException("Unexpected type '" + parts[0] + "'");
        }
        return new WeightedAliasTarget(HostName.from(parts[1]), parts[2], ZoneId.from(parts[3]),
                                       Long.parseLong(parts[4]));
    }

}
