// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.configserver.noderepository.bindings;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.List;

/**
 * This class represents a response from the /nodes/v2/node/ API. It is designed to be
 * usable by any module, by not depending itself on any module-specific classes.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class GetNodesResponse {

    public final List<NodeRepositoryNode> nodes;

    @JsonCreator
    public GetNodesResponse(@JsonProperty("nodes") List<NodeRepositoryNode> nodes) {
        this.nodes = Collections.unmodifiableList(nodes);
    }

}
