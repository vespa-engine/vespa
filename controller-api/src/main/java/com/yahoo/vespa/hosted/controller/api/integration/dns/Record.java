// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.dns;

import java.util.Objects;

/**
 * A basic representation of a DNS resource record, containing only the record type, name and value.
 *
 * @author mpolden
 */
public class Record {

    private final Type type;
    private final String name;
    private final String value;

    public Record(Type type, String name, String value) {
        this.type = type;
        this.name = name;
        this.value = value;
    }

    public Record(String type, String name, String value) {
        this(Type.valueOf(type), name, value);
    }

    public Type type() {
        return type;
    }

    public String value() {
        return value;
    }

    public String name() {
        return name;
    }

    public enum Type {
        A,
        AAAA,
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
        return "Record{" +
                "type=" + type +
                ", name='" + name + '\'' +
                ", value='" + value + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Record)) return false;
        Record record = (Record) o;
        return type == record.type &&
                Objects.equals(name, record.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, name);
    }

}
