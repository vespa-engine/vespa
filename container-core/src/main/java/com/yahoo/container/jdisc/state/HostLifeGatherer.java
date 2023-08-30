// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc.state;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yahoo.component.Vtag;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;

/**
 * @author olaa
 */
public class HostLifeGatherer {

    private static final ObjectMapper jsonMapper = new ObjectMapper();

    public static JsonNode getHostLifePacket() {
        ObjectNode jsonObject = jsonMapper.createObjectNode();
        jsonObject.put("timestamp", Instant.now().getEpochSecond());
        jsonObject.put("application", "host_life");
        ObjectNode metrics = jsonMapper.createObjectNode();
        metrics.put("alive", 1);
        jsonObject.set("metrics", metrics);
        ObjectNode dimensions = jsonMapper.createObjectNode();
        dimensions.put("vespaVersion", Vtag.currentVersion.toFullString());
        jsonObject.set("dimensions", dimensions);
        return jsonObject;
    }

}
