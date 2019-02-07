// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.dns;

import java.util.Objects;

/**
 * Represents the name field of a DNS record (NAME). This is typically a FQDN.
 *
 * @author mpolden
 */
public class RecordName {

    private final String name;

    private RecordName(String name) {
        this.name = Objects.requireNonNull(name, "name cannot be null");
    }

    public String asString() {
        return name;
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

}
