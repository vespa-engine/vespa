// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http;

import com.yahoo.config.SimpletypesConfig;
import com.yahoo.config.codegen.DefParser;
import com.yahoo.config.codegen.InnerCNode;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.logging.AccessLog;
import com.yahoo.text.StringUtilities;
import com.yahoo.vespa.config.ConfigKey;
import com.yahoo.vespa.config.ConfigPayload;
import com.yahoo.vespa.config.server.rpc.MockRequestHandler;
import com.yahoo.vespa.config.protocol.SlimeConfigResponse;
import com.yahoo.config.provision.ApplicationId;

import org.junit.Before;
import org.junit.Test;
import java.io.IOException;
import java.io.StringReader;
import java.util.Collections;
import java.util.HashSet;
import java.util.concurrent.Executor;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.*;

import static com.yahoo.jdisc.http.HttpRequest.Method.GET;
import static com.yahoo.jdisc.http.HttpResponse.Status.*;


/**
 * @author lulf
 * @since 5.1
 */
public class HttpGetConfigHandlerTest {
    private static final String configUri = "http://yahoo.com:8080/config/v1/foo.bar/myid";

    private MockRequestHandler mockRequestHandler;
    private HttpGetConfigHandler handler;

    @Before
    public void setUp() {
        mockRequestHandler = new MockRequestHandler();
        mockRequestHandler.setAllConfigs(new HashSet<ConfigKey<?>>() {{ 
            add(new ConfigKey<>("bar", "myid", "foo"));
            }} );
        handler = new HttpGetConfigHandler(
                HttpGetConfigHandler.testOnlyContext(),
                mockRequestHandler);
    }

    @Test
    public void require_that_handler_can_be_created() throws IOException {
        // Define config response for mock handler
        final long generation = 1L;
        ConfigPayload payload = ConfigPayload.fromInstance(new SimpletypesConfig(new SimpletypesConfig.Builder()));
        InnerCNode targetDef = getInnerCNode();
        mockRequestHandler.responses.put(ApplicationId.defaultId(), SlimeConfigResponse.fromConfigPayload(payload, targetDef, generation, false, "mymd5"));
        HttpResponse response = handler.handle(HttpRequest.createTestRequest(configUri, GET));
        assertThat(SessionHandlerTest.getRenderedString(response), is("{\"boolval\":false,\"doubleval\":0.0,\"enumval\":\"VAL1\",\"intval\":0,\"longval\":0,\"stringval\":\"s\"}"));
    }
    
    @Test
    public void require_correct_error_response() throws IOException {
        final String nonExistingConfigNameUri = "http://yahoo.com:8080/config/v1/nonexisting.config/myid";
        final String nonExistingConfigUri = "http://yahoo.com:8080/config/v1/foo.bar/myid/nonexisting/id";
        final String illegalConfigNameUri = "http://yahoo.com:8080/config/v1/foobar/myid";

        HttpResponse response = handler.handle(HttpRequest.createTestRequest(nonExistingConfigNameUri, GET));
        HandlerTest.assertHttpStatusCodeErrorCodeAndMessage(response, NOT_FOUND, HttpErrorResponse.errorCodes.NOT_FOUND, "No such config: nonexisting.config");
        assertTrue(SessionHandlerTest.getRenderedString(response).contains("No such config:"));
        response = handler.handle(HttpRequest.createTestRequest(nonExistingConfigUri, GET));
        HandlerTest.assertHttpStatusCodeErrorCodeAndMessage(response, NOT_FOUND, HttpErrorResponse.errorCodes.NOT_FOUND, "No such config id: myid/nonexisting/id");
        assertEquals(response.getContentType(), "application/json");
        assertTrue(SessionHandlerTest.getRenderedString(response).contains("No such config id:"));
        response = handler.handle(HttpRequest.createTestRequest(illegalConfigNameUri, GET));
        HandlerTest.assertHttpStatusCodeErrorCodeAndMessage(response, BAD_REQUEST, HttpErrorResponse.errorCodes.BAD_REQUEST, "Illegal config, must be of form namespace.name.");
    }

    @Test
    public void require_that_nocache_property_works() throws IOException {
        long generation = 1L;
        ConfigPayload payload = ConfigPayload.fromInstance(new SimpletypesConfig(new SimpletypesConfig.Builder()));
        InnerCNode targetDef = getInnerCNode();
        mockRequestHandler.responses.put(ApplicationId.defaultId(), SlimeConfigResponse.fromConfigPayload(payload, targetDef, generation, false, "mymd5"));
        final HttpRequest request = HttpRequest.createTestRequest(configUri, GET, null, Collections.singletonMap("nocache", "true"));
        HttpResponse response = handler.handle(request);
        assertThat(SessionHandlerTest.getRenderedString(response), is("{\"boolval\":false,\"doubleval\":0.0,\"enumval\":\"VAL1\",\"intval\":0,\"longval\":0,\"stringval\":\"s\"}"));
    }

    private InnerCNode getInnerCNode() {
        // TODO: Hope to be able to remove this mess soon.
        DefParser dParser = new DefParser(SimpletypesConfig.getDefName(), new StringReader(StringUtilities.implode(SimpletypesConfig.CONFIG_DEF_SCHEMA, "\n")));
        return dParser.getTree();
    }
}
