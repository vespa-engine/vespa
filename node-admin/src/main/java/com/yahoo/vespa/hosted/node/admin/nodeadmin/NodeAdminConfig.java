// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.nodeadmin;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;

@JsonIgnoreProperties(ignoreUnknown = true)
public class NodeAdminConfig {
    private static final ObjectMapper mapper = new ObjectMapper();

    /**
     * If null, the default admin component will be used.
     */
    @JsonProperty("main-component")
    public String mainComponent = null;

    public static NodeAdminConfig fromFile(File file) {
        if (!file.exists()) {
            return new NodeAdminConfig();
        }

        try {
            return mapper.readValue(file, NodeAdminConfig.class);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read " + file + " as a " +
                    NodeAdminConfig.class.getName(), e);
        }
    }
}
