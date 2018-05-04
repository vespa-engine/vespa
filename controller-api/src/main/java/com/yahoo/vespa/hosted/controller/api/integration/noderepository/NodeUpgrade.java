// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.noderepository;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * @author mpolden
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class NodeUpgrade {

    @JsonProperty("version")
    private final String version;

    @JsonProperty("force")
    private final boolean force;

    @JsonCreator
    public NodeUpgrade(@JsonProperty("version") String version, @JsonProperty("force") boolean force) {
        this.version = version;
        this.force = force;
    }

    public String getVersion() {
        return version;
    }

    public boolean isForce() {
        return force;
    }

}
