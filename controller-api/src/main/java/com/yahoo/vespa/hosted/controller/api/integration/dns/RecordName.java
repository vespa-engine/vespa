// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.dns;

import java.util.Objects;

/**
 * Represents the name field of a DNS record (NAME).
 *
 * @author mpolden
 */
public record RecordName(String name) implements Comparable<RecordName> {

    public RecordName {
        Objects.requireNonNull(name, "name cannot be null");
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
    public String toString() {
        return name;
    }

    @Override
    public int compareTo(RecordName that) {
        return this.name.compareTo(that.name);
    }

    public static RecordName from(String name) {
        return new RecordName(name);
    }

    public static RecordName fqdn(String name) {
        return from(name).asFqdn();
    }

}
