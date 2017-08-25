// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.zone.v2;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.yahoo.vespa.hosted.controller.api.configserver.Environment;
import com.yahoo.vespa.hosted.controller.api.configserver.Region;

/**
 * @author mpolden
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ZoneReference {

    @JsonProperty("environment")
    public final Environment environment;
    @JsonProperty("region")
    public final Region region;

    @JsonCreator
    public ZoneReference(@JsonProperty("environment") Environment environment, @JsonProperty("region") Region region) {
        this.environment = environment;
        this.region = region;
    }

}
