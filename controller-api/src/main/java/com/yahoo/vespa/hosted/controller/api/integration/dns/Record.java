// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.dns;

import java.util.Comparator;
import java.util.Objects;

/**
 * A basic representation of a DNS resource record, containing the record type, name and data.
 *
 * @author mpolden
 */
public class Record implements Comparable<Record> {

    private static final Comparator<Record> comparator = Comparator.comparing(Record::type)
                                                                   .thenComparing(Record::name)
                                                                   .thenComparing(Record::data);

    private final Type type;
    private final RecordName name;
    private final RecordData data;

    public Record(Type type, RecordName name, RecordData data) {
        this.type = Objects.requireNonNull(type, "type cannot be null");
        this.name = Objects.requireNonNull(name, "name cannot be null");
        this.data = Objects.requireNonNull(data, "data cannot be null");
    }

    /** DNS type of this */
    public Type type() {
        return type;
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
        MX,
        NS,
        PTR,
        SOA,
        SRV,
        TXT
    }

    @Override
    public String toString() {
        return String.format("%s %s -> %s", type, name, data);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Record record = (Record) o;
        return type == record.type &&
               name.equals(record.name) &&
               data.equals(record.data);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, name, data);
    }

    @Override
    public int compareTo(Record that) {
        return comparator.compare(this, that);
    }

}
