// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.dns;

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

    public String id() {
        return id;
    }

    @Override
    public String toString() {
        return "RecordId{" +
                "id='" + id + '\'' +
                '}';
    }
}
