// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.configserver.noderepository.bindings;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Automagically handles (de)serialization based on 1:1 message fields and identifier names.
 * Deserializes JSON strings on the form:
 * <pre>
 *   {
 *     "message": "Updated host.com"
 *   }
 * </pre>
 *
 * @author bakksjo
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class UpdateNodeAttributesResponse {
    @JsonProperty("message")
    public String message;
    @JsonProperty("error-code")
    public String errorCode;
}
