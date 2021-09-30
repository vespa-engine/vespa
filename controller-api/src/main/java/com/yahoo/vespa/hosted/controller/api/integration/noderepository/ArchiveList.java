// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.noderepository;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ArchiveList {
    @JsonProperty("archives")
    public List<Archive> archives = new ArrayList<>();

    public static class Archive {
        @JsonProperty("tenant")
        public String tenant;

        @JsonProperty("uri")
        public String uri;
    }
}
