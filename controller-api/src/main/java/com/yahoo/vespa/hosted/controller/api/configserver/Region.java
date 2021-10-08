// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.configserver;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonValue;
import com.yahoo.config.provision.RegionName;

/**
 * Region representation using the same definition as configserver. And allowing
 * serialization/deserialization to/from JSON.
 *
 * @author Ulf Lilleengen
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Region {
    private final RegionName region;

    public Region(RegionName region) {
        this.region = region;
    }

    @JsonValue
    public String value() {
        return region.value();
    }

    @Override
    public String toString() { return value(); }

    public RegionName getRegion() {
        return region;
    }

    @JsonCreator
    public Region(String region) {
        this.region = com.yahoo.config.provision.RegionName.from(region);
    }
}
