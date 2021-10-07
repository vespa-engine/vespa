// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.dns;

import java.util.Objects;

/**
 * Represents the data field of a DNS record (RDATA).
 *
 * E.g. this may be an IP address for A records, or a FQDN for CNAME records.
 *
 * @author mpolden
 */
public class RecordData implements Comparable<RecordData> {

    private final String data;

    private RecordData(String data) {
        this.data = Objects.requireNonNull(data, "data cannot be null");
    }

    public String asString() {
        return data;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RecordData that = (RecordData) o;
        return Objects.equals(data, that.data);
    }

    @Override
    public int hashCode() {
        return Objects.hash(data);
    }

    @Override
    public String toString() {
        return data;
    }

    /** Create data containing the given data */
    public static RecordData from(String data) {
        return new RecordData(data);
    }

    /** Create a new record and append a trailing dot to given data, if missing */
    public static RecordData fqdn(String data) {
        return from(data.endsWith(".") ? data : data + ".");
    }

    @Override
    public int compareTo(RecordData that) {
        return this.data.compareTo(that.data);
    }

}
