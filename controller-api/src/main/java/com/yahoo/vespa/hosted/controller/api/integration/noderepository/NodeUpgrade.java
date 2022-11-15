// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.noderepository;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * @author mpolden
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NodeUpgrade {

    @JsonProperty("version")
    private final String version;

    @JsonProperty("osVersion")
    private final String osVersion;

    @JsonProperty("force")
    private final boolean force;

    @JsonCreator
    public NodeUpgrade(@JsonProperty("version") String version, @JsonProperty("osVersion") String osVersion,
                       @JsonProperty("force") boolean force) {
        this.version = version;
        this.osVersion = osVersion;
        this.force = force;
    }

    public String getVersion() {
        return version;
    }

    public String getOsVersion() {
        return osVersion;
    }

    public boolean isForce() {
        return force;
    }

}
