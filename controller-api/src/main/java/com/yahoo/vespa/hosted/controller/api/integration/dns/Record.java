// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.dns;

import java.util.Objects;

/**
 * A basic representation of a DNS resource record, containing the record id, type, name and value.
 *
 * @author mpolden
 */
public class Record {

    private final RecordId id;
    private final Type type;
    private final RecordName name;
    private final RecordData data;

    public Record(RecordId id, Type type, RecordName name, RecordData data) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.type = Objects.requireNonNull(type, "type cannot be null");
        this.name = Objects.requireNonNull(name, "name cannot be null");
        this.data = Objects.requireNonNull(data, "data cannot be null");
    }

    /** Unique identifier for this */
    public RecordId id() {
        return id;
    }

    /** DNS type of this */
    public Type type() {
        return type;
    }

    /** Data in this, e.g. IP address for "A" record */
    public RecordData data() {
        return data;
    }

    /** Name of this, e.g. a FQDN for "A" record */
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
        return String.format("%s: %s %s -> %s", id, type, name, data);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Record record = (Record) o;
        return Objects.equals(id, record.id) &&
               type == record.type &&
               Objects.equals(name, record.name) &&
               Objects.equals(data, record.data);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, type, name, data);
    }
}
