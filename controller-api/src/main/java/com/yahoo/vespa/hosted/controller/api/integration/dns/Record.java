// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.dns;

import java.time.Duration;
import java.util.Comparator;
import java.util.Objects;

/**
 * A basic representation of a DNS resource record, containing the record type, name and data.
 *
 * @author mpolden
 */
public record Record(Type type,
                     Duration ttl,
                     RecordName name,
                     RecordData data) implements Comparable<Record> {

    private static final Comparator<Record> comparator = Comparator.comparing(Record::type)
                                                                   .thenComparing(Record::name)
                                                                   .thenComparing(Record::data);

    public Record {
        Objects.requireNonNull(type, "type cannot be null");
        Objects.requireNonNull(ttl, "ttl cannot be null");
        Objects.requireNonNull(name, "name cannot be null");
        Objects.requireNonNull(data, "data cannot be null");
    }

    public Record(Type type, RecordName name, RecordData data) {
        this(type, Duration.ofMinutes(5), name, data);
    }

    /** DNS type of this */
    public Type type() {
        return type;
    }

    /** The TTL value of this */
    public Duration ttl() {
        return ttl;
    }

    /** Data in this, e.g. IP address for records of type A */
    public RecordData data() {
        return data;
    }

    /** Name of this, e.g. a FQDN for records of type A */
    public RecordName name() {
        return name;
    }

    public enum Type {
        A,
        AAAA,
        ALIAS,
        CNAME,
        DIRECT,
        MX,
        NS,
        PTR,
        SOA,
        SRV,
        TXT,
        SPF,
        NAPTR,
        CAA,
    }

    @Override
    public String toString() {
        return String.format("%s %s -> %s [TTL: %s]", type, name, data, ttl);
    }

    @Override
    public int compareTo(Record that) {
        return comparator.compare(this, that);
    }

}
