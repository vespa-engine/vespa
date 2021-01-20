// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.vespa.config.server.TestComponentRegistry;
import com.yahoo.vespa.config.server.http.SessionHandlerTest;
import org.junit.Test;

import java.io.IOException;

import static com.yahoo.jdisc.http.HttpRequest.Method.GET;
import static org.junit.Assert.assertEquals;

/**
 * @author hmusum
 */
public class StatusHandlerTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    public void require_that_handler_works() throws IOException {
        TestComponentRegistry componentRegistry = new TestComponentRegistry.Builder().build();
        ConfigserverConfig configserverConfig = new ConfigserverConfig.Builder().build();
        StatusHandler handler = new StatusHandler(StatusHandler.testOnlyContext(), componentRegistry, configserverConfig);

        HttpResponse response = handler.handle(HttpRequest.createTestRequest("/status", GET));
        JsonNode jsonNode = mapper.readTree(SessionHandlerTest.getRenderedString(response));

        assertEquals(configserverConfig.rpcport(), jsonNode.get("configserverConfig").get("rpcport").asInt());
        assertEquals(configserverConfig.applicationDirectory(), jsonNode.get("configserverConfig").get("applicationDirectory").asText());

        assertEquals(1, jsonNode.get("modelVersions").size());
    }

}
