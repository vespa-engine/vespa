// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http.v2;

import static com.yahoo.jdisc.Response.Status.BAD_REQUEST;
import static com.yahoo.jdisc.Response.Status.NOT_FOUND;
import static com.yahoo.jdisc.http.HttpRequest.Method.GET;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.*;
import java.io.IOException;
import java.io.StringReader;
import java.util.Collections;
import java.util.HashSet;

import com.yahoo.config.provision.TenantName;
import com.yahoo.vespa.config.server.http.HttpErrorResponse;
import com.yahoo.vespa.config.server.tenant.TenantRepository;
import org.junit.Before;
import org.junit.Test;
import com.yahoo.config.SimpletypesConfig;
import com.yahoo.config.codegen.DefParser;
import com.yahoo.config.codegen.InnerCNode;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.text.StringUtilities;
import com.yahoo.vespa.config.ConfigKey;
import com.yahoo.vespa.config.ConfigPayload;
import com.yahoo.vespa.config.protocol.SlimeConfigResponse;
import com.yahoo.vespa.config.server.rpc.MockRequestHandler;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.config.server.http.HandlerTest;
import com.yahoo.vespa.config.server.http.HttpConfigRequest;
import com.yahoo.vespa.config.server.http.SessionHandlerTest;

public class HttpGetConfigHandlerTest {

    private static final TenantName tenant = TenantName.from("mytenant");
    private static final String EXPECTED_RENDERED_STRING = "{\"boolval\":false,\"doubleval\":0.0,\"enumval\":\"VAL1\",\"intval\":0,\"longval\":0,\"stringval\":\"s\"}";
    private static final String configUri = "http://yahoo.com:8080/config/v2/tenant/" + tenant.value() + "/application/myapplication/foo.bar/myid";
    private MockRequestHandler mockRequestHandler;
    private HttpGetConfigHandler handler;

    @Before
    public void setUp() throws Exception {        
        mockRequestHandler = new MockRequestHandler();
        mockRequestHandler.setAllConfigs(new HashSet<ConfigKey<?>>() {{ 
            add(new ConfigKey<>("bar", "myid", "foo"));
            }} );        
        TestTenantBuilder tb = new TestTenantBuilder();
        tb.createTenant(tenant).withRequestHandler(mockRequestHandler).build();
        TenantRepository tenantRepository = tb.createTenants();
        handler = new HttpGetConfigHandler(
                HttpGetConfigHandler.testOnlyContext(),
                tenantRepository);
    }

    @Test
    public void require_that_handler_can_be_created() throws IOException {
        // Define config response for mock handler
        final long generation = 1L;
        ConfigPayload payload = ConfigPayload.fromInstance(new SimpletypesConfig(new SimpletypesConfig.Builder()));
        InnerCNode targetDef = getInnerCNode();
        mockRequestHandler.responses.put(new ApplicationId.Builder().tenant(tenant).applicationName("myapplication").build(),
                                         SlimeConfigResponse.fromConfigPayload(payload, targetDef, generation, "mymd5"));
        HttpResponse response = handler.handle(HttpRequest.createTestRequest(configUri, GET));
        assertThat(SessionHandlerTest.getRenderedString(response), is(EXPECTED_RENDERED_STRING));
    }
    
    @Test
    public void require_that_handler_can_handle_long_appid_request_with_configid() throws IOException {
        String uriLongAppId = "http://yahoo.com:8080/config/v2/tenant/" + tenant.value() +
                              "/application/myapplication/environment/staging/region/myregion/instance/myinstance/foo.bar/myid";
        final long generation = 1L;
        ConfigPayload payload = ConfigPayload.fromInstance(new SimpletypesConfig(new SimpletypesConfig.Builder()));
        InnerCNode targetDef = getInnerCNode();
        mockRequestHandler.responses.put(new ApplicationId.Builder()
                                         .tenant(tenant)
                                         .applicationName("myapplication").instanceName("myinstance").build(),
                                         SlimeConfigResponse.fromConfigPayload(payload, targetDef, generation, "mymd5"));
        HttpResponse response = handler.handle(HttpRequest.createTestRequest(uriLongAppId, GET));
        assertThat(SessionHandlerTest.getRenderedString(response), is(EXPECTED_RENDERED_STRING));
    }

    @Test
    public void require_that_request_gets_correct_fields_with_full_appid() {
        String uriLongAppId = "http://yahoo.com:8080/config/v2/tenant/bill/application/sookie/environment/dev/region/bellefleur/instance/sam/foo.bar/myid";
        HttpRequest r = HttpRequest.createTestRequest(uriLongAppId, GET);
        HttpConfigRequest req = HttpConfigRequest.createFromRequestV2(r);
        assertThat(req.getApplicationId().tenant().value(), is("bill"));
        assertThat(req.getApplicationId().application().value(), is("sookie"));
        assertThat(req.getApplicationId().instance().value(), is("sam"));
    }

    @Test
    public void require_that_request_gets_correct_fields_with_short_appid() {
        String uriShortAppId = "http://yahoo.com:8080/config/v2/tenant/jason/application/alcide/foo.bar/myid";
        HttpRequest r = HttpRequest.createTestRequest(uriShortAppId, GET);
        HttpConfigRequest req = HttpConfigRequest.createFromRequestV2(r);
        assertThat(req.getApplicationId().tenant().value(), is("jason"));
        assertThat(req.getApplicationId().application().value(), is("alcide"));
        assertThat(req.getApplicationId().instance().value(), is("default"));
    }

    @Test
    public void require_correct_error_response() throws IOException {
        final String nonExistingConfigNameUri = "http://yahoo.com:8080/config/v2/tenant/mytenant/application/myapplication/nonexisting.config/myid";
        final String nonExistingConfigUri = "http://yahoo.com:8080/config/v2/tenant/mytenant/application/myapplication//foo.bar/myid/nonexisting/id";
        final String illegalConfigNameUri = "http://yahoo.com:8080/config/v2/tenant/mytenant/application/myapplication//foobar/myid";

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
        mockRequestHandler.responses.put(new ApplicationId.Builder().tenant(tenant).applicationName("myapplication").build(),
                                         SlimeConfigResponse.fromConfigPayload(payload, targetDef, generation, "mymd5"));
        final HttpRequest request = HttpRequest.createTestRequest(configUri, GET, null, Collections.singletonMap("nocache", "true"));
        HttpResponse response = handler.handle(request);
        assertThat(SessionHandlerTest.getRenderedString(response), is(EXPECTED_RENDERED_STRING));
    }

    private InnerCNode getInnerCNode() {
        // TODO: Hope to be able to remove this mess soon.
        DefParser dParser = new DefParser(SimpletypesConfig.getDefName(), new StringReader(StringUtilities.implode(SimpletypesConfig.CONFIG_DEF_SCHEMA, "\n")));
        return dParser.getTree();
    }

}
