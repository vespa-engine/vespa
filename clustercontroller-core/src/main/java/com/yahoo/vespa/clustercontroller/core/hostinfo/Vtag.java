// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core.hostinfo;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Class for handling version.
 *
 * @author Haakon Dybdahl
 */
public class Vtag {

    private final String version;

    @JsonCreator
    public Vtag(@JsonProperty("version") String version) {
        this.version = version;
    }

    public String getVersionOrNull() {
        return version;
    }

}
