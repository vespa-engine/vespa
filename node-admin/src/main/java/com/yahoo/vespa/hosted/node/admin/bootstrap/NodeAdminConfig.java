// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.bootstrap;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(value = JsonInclude.Include.NON_NULL)
public class NodeAdminConfig {
    private static final ObjectMapper mapper = new ObjectMapper();

    enum Mode {
        TENANT,
        CONFIG_SERVER_HOST
    }

    @JsonProperty("mode")
    public Mode mode = Mode.TENANT;

    public static NodeAdminConfig get() {
        // TODO: Get config from file
        return new NodeAdminConfig();
    }
}
