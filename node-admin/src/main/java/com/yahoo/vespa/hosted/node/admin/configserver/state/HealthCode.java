// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.configserver.state;

/**
 * The healthiness of a remote Vespa server based on REST API
 *
 * @author hakon
 */
public enum HealthCode {
    DOWN("down"),
    INITIALIZING("initializing"),
    UP("up");

    private final String code;

    HealthCode(String code) {
        this.code = code;
    }

    public static HealthCode fromString(String code) {
        return HealthCode.valueOf(code.toUpperCase());
    }

    public String asString() {
        return code;
    }

    @Override
    public String toString() {
        return asString();
    }
}
