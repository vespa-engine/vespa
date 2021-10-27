// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.noderepository;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * @author mpolden
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class NodeTargetVersions {

    @JsonProperty("versions")
    private final Map<NodeType, String> vespaVersions;

    @JsonProperty("osVersions")
    private final Map<NodeType, String> osVersions;

    @JsonCreator
    public NodeTargetVersions(@JsonProperty("versions") Map<NodeType, String> vespaVersions,
                              @JsonProperty("osVersions") Map<NodeType, String> osVersions) {
        this.vespaVersions = Map.copyOf(vespaVersions);
        this.osVersions = Map.copyOf(osVersions);
    }

    public Map<NodeType, String> vespaVersions() {
        return vespaVersions;
    }

    public Map<NodeType, String> osVersions() {
        return osVersions;
    }

}
