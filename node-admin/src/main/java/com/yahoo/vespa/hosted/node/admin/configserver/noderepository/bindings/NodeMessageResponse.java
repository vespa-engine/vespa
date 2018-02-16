// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.configserver.noderepository.bindings;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response from PUT /nodes/v2/state/ call to node-repository.
 *
 * @author dybis
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class NodeMessageResponse {
    @JsonProperty("message")
    public String message;
    @JsonProperty("error-code")
    public String errorCode;
}
