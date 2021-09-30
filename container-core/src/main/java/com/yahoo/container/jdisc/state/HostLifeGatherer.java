// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc.state;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;

/**
 * @author olaa
 */
public class HostLifeGatherer {

    private static final ObjectMapper jsonMapper = new ObjectMapper();

    private static final Path UPTIME_PATH = Path.of("/proc");

    public static JsonNode getHostLifePacket(FileWrapper fileWrapper) {
        long upTime;
        int statusCode = 0;
        String statusMessage = "OK";

        try {
            upTime = fileWrapper.getFileAgeInSeconds(UPTIME_PATH);
        } catch (IOException e) {
            upTime = 0;
            statusCode = 1;
            statusMessage = "Unable to read proc folder";
        }


        ObjectNode jsonObject = jsonMapper.createObjectNode();
        jsonObject.put("status_code", statusCode);
        jsonObject.put("status_msg", statusMessage);
        jsonObject.put("timestamp", Instant.now().getEpochSecond());
        jsonObject.put("application", "host_life");
        ObjectNode metrics = jsonMapper.createObjectNode();
        metrics.put("uptime", upTime);
        metrics.put("alive", 1);
        jsonObject.set("metrics", metrics);
        return jsonObject;
    }

}
