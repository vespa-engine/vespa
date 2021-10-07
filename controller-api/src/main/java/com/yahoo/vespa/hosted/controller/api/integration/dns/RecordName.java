// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.dns;

import java.util.Objects;

/**
 * Represents the name field of a DNS record (NAME).
 *
 * @author mpolden
 */
public class RecordName implements Comparable<RecordName> {

    private final String name;

    private RecordName(String name) {
        this.name = Objects.requireNonNull(name, "name cannot be null");
    }

    public String asString() {
        return name;
    }

    /** Returns whether this is a fully qualified domain name (ends in trailing dot) */
    public boolean isFqdn() {
        return name.endsWith(".");
    }

    /** Returns this as a fully qualified domain name (ends in trailing dot) */
    public RecordName asFqdn() {
        return isFqdn() ? this : new RecordName(name + ".");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RecordName that = (RecordName) o;
        return Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    @Override
    public String toString() {
        return name;
    }

    public static RecordName from(String name) {
        return new RecordName(name);
    }

    public static RecordName fqdn(String name) {
        return from(name).asFqdn();
    }

    @Override
    public int compareTo(RecordName that) {
        return this.name.compareTo(that.name);
    }

}
