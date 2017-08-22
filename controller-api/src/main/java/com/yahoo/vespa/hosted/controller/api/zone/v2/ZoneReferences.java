// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.zone.v2;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.List;

/**
 * Wire format for listing the controller URIs for all the available zones
 *
 * @author smorgrav
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ZoneReferences {

    @JsonProperty("uris")
    public final List<String> uris;

    @JsonProperty("zones")
    public final List<ZoneReference> zones;

    @JsonCreator
    public ZoneReferences(@JsonProperty("uris") List<String> uris, @JsonProperty("zones") List<ZoneReference> zones) {
        this.uris = Collections.unmodifiableList(uris);
        this.zones = Collections.unmodifiableList(zones);
    }

}
