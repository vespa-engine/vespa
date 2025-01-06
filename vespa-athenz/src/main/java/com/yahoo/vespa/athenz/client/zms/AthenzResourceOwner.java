//  Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.athenz.client.zms;

public enum AthenzResourceOwner {
    NONE(null),
    CONTROLLER("CONTROLLER");

    private final String value;
    AthenzResourceOwner(String value) {
        this.value = value;
    }
    boolean isSet() {
        return value != null;
    }
    public String value() {
        return value;
    }
}
