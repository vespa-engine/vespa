// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.noderepository;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * @author mortent
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class NodeList {
    @JsonProperty
    List<NodeRepositoryNode> nodes;

    public NodeList() {
    }

    public NodeList(List<NodeRepositoryNode> nodes) {
        this.nodes = nodes;
    }

    public List<NodeRepositoryNode> nodes() {
        return nodes;
    }
}
