// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.configserver.state;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class HealthResponseTest {
    @Test
    public void deserializationOfNormalResponse() throws Exception {
        String jsonResponse = "{\n" +
                "    \"metrics\": {\n" +
                "        \"snapshot\": {\n" +
                "            \"from\": 1.523614569023E9,\n" +
                "            \"to\": 1.523614629023E9\n" +
                "        },\n" +
                "        \"values\": [\n" +
                "            {\n" +
                "                \"name\": \"requestsPerSecond\",\n" +
                "                \"values\": {\n" +
                "                    \"count\": 121,\n" +
                "                    \"rate\": 2.0166666666666666\n" +
                "                }\n" +
                "            },\n" +
                "            {\n" +
                "                \"name\": \"latencySeconds\",\n" +
                "                \"values\": {\n" +
                "                    \"average\": 5.537190082644628E-4,\n" +
                "                    \"count\": 121,\n" +
                "                    \"last\": 0.001,\n" +
                "                    \"max\": 0.001,\n" +
                "                    \"min\": 0,\n" +
                "                    \"rate\": 2.0166666666666666\n" +
                "                }\n" +
                "            }\n" +
                "        ]\n" +
                "    },\n" +
                "    \"status\": {\"code\": \"up\"},\n" +
                "    \"time\": 1523614629451\n" +
                "}";

        HealthResponse response = deserialize(jsonResponse);

        assertEquals(response.status.code, "up");
    }

    private static HealthResponse deserialize(String json) throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        return mapper.readValue(json, HealthResponse.class);
    }
}