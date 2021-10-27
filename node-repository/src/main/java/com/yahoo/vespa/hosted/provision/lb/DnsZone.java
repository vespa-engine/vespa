// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.lb;

import java.util.Objects;

/**
 * Represents a hosted DNS zone.
 *
 * @author mpolden
 */
public class DnsZone {

    private final String id;

    public DnsZone(String id) {
        this.id = Objects.requireNonNull(id, "id must be non-null");
    }

    /** The unique ID of this */
    public String id() {
        return id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DnsZone dnsZone = (DnsZone) o;
        return id.equals(dnsZone.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "DNS zone '" + id + "'";
    }

}
