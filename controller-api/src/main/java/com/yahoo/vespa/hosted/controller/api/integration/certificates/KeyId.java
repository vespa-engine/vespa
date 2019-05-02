// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.certificates;

/**
 * Identifier for a key pair. Used for persisting/retrieving a key pair.
 *
 * @author mortent
 * @author andreer
 */
public class KeyId {
    private final String name;
    private final int version;

    public KeyId(String name, int version) {
        this.name = name;
        this.version = version;
    }

    public String name() {
        return name;
    }

    public int version() {
        return version;
    }
}
