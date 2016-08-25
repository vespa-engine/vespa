package com.yahoo.vespa.hosted.node.admin.noderepository.bindings;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response from node/ready call from node-repo.
 *
 * @author dybis
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class NodeReadyResponse {
    @JsonProperty("message")
    public String message;
}