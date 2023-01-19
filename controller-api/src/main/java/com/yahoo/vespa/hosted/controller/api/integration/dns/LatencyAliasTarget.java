// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.dns;

import ai.vespa.http.DomainName;
import com.yahoo.config.provision.zone.ZoneId;

import java.util.Objects;

/**
 * An implementation of {@link AliasTarget} that uses latency-based routing.
 *
 * @author mpolden
 */
public final class LatencyAliasTarget extends AliasTarget {

    static final String TARGET_TYPE = "latency";

    private final ZoneId zone;

    public LatencyAliasTarget(DomainName name, String dnsZone, ZoneId zone) {
        super(name, dnsZone, zone.value());
        this.zone = Objects.requireNonNull(zone);
    }

    /** The zone this record points to */
    public ZoneId zone() {
        return zone;
    }

    @Override
    public RecordData pack() {
        return RecordData.from(String.join("/", TARGET_TYPE, name().value(), dnsZone(), id()));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        LatencyAliasTarget that = (LatencyAliasTarget) o;
        return zone.equals(that.zone);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), zone);
    }

    @Override
    public String toString() {
        return "latency target for " + name() + " [id=" + id() + ",dnsZone=" + dnsZone() + "]";
    }

    /** Unpack latency alias from given record data */
    public static LatencyAliasTarget unpack(RecordData data) {
        var parts = data.asString().split("/");
        if (parts.length != 4) {
            throw new IllegalArgumentException("Expected data to be on format type/name/DNS-zone/zone-id, but got " +
                                               data.asString());
        }
        if (!TARGET_TYPE.equals(parts[0])) {
            throw new IllegalArgumentException("Unexpected type '" + parts[0] + "'");
        }
        return new LatencyAliasTarget(DomainName.of(parts[1]), parts[2], ZoneId.from(parts[3]));
    }

}
