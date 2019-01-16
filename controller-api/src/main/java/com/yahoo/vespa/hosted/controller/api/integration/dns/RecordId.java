// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.dns;

import java.util.Objects;

/**
 * Unique identifier for a resource record.
 *
 * @author mpolden
 */
public class RecordId {

    private final String id;

    public RecordId(String id) {
        this.id = id;
    }

    public String asString() {
        return id;
    }

    @Override
    public String toString() {
        return "RecordId{" +
                "id='" + id + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RecordId recordId = (RecordId) o;
        return id.equals(recordId.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
