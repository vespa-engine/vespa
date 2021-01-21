// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.config.model.NullConfigModelRegistry;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.vespa.config.server.http.SessionHandlerTest;
import com.yahoo.vespa.config.server.modelfactory.ModelFactoryRegistry;
import com.yahoo.vespa.model.VespaModelFactory;
import org.junit.Test;

import java.io.IOException;
import java.util.List;

import static com.yahoo.jdisc.http.HttpRequest.Method.GET;
import static org.junit.Assert.assertEquals;

/**
 * @author hmusum
 */
public class StatusHandlerTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    public void require_that_handler_works() throws IOException {
        ModelFactoryRegistry modelFactoryRegistry = new ModelFactoryRegistry(List.of(new VespaModelFactory(new NullConfigModelRegistry())));
        ConfigserverConfig configserverConfig = new ConfigserverConfig.Builder().build();
        StatusHandler handler = new StatusHandler(StatusHandler.testOnlyContext(), modelFactoryRegistry, configserverConfig);

        HttpResponse response = handler.handle(HttpRequest.createTestRequest("/status", GET));
        JsonNode jsonNode = mapper.readTree(SessionHandlerTest.getRenderedString(response));

        assertEquals(configserverConfig.rpcport(), jsonNode.get("configserverConfig").get("rpcport").asInt());
        assertEquals(configserverConfig.applicationDirectory(), jsonNode.get("configserverConfig").get("applicationDirectory").asText());

        assertEquals(1, jsonNode.get("modelVersions").size());
    }

}
