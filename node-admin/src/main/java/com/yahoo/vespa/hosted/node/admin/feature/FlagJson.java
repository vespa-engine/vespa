// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.hosted.node.admin.feature;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import javax.annotation.concurrent.Immutable;

/**
 * @author hakon
 */
@Immutable
class FlagJson {
    private boolean enabled;

    @JsonCreator
    FlagJson(@JsonProperty(value = "enabled", defaultValue = "false")
                    boolean enabled) {
        this.enabled = enabled;
    }

    boolean enabled() {
        return enabled;
    }

    String asJson() {
        return "{ \"enabled\": \"" + enabled + "\" }";
    }

    @Override
    public String toString() {
        return asJson();
    }
}
