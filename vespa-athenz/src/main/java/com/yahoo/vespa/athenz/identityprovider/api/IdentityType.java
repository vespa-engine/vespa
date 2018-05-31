// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.identityprovider.api;

import java.util.Arrays;

/**
 * Represents the types of identities that the configserver can provide.
 *
 * @author bjorncs
 */
public enum IdentityType {TENANT("tenant"), NODE("node");
    private final String id;

    IdentityType(String id) { this.id = id; }

    public String id() { return id; }

    public static IdentityType fromId(String id) {
        return Arrays.stream(values())
                .filter(v -> v.id.equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Invalid id: " + id));
    }
}

