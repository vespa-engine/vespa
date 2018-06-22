// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.statuspage;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.yahoo.application.Networking;
import com.yahoo.application.container.JDisc;
import com.yahoo.application.container.handler.Request;
import com.yahoo.application.container.handler.Response;
import com.yahoo.vespa.hosted.controller.MockSecretStore;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.junit.Assert.assertEquals;

/**
 * @author mpolden
 */
public class StatusPageProxyApiTest {

    private JDisc container;

    @Before
    public void startContainer() {
        container = JDisc.fromServicesXml(servicesXml(), Networking.disable);
        secretStore().setSecret("vespa_hosted.controller.statuspage_api_key", "page-id:secret");
    }

    @After
    public void stopContainer() {
        container.close();
        secretStore().clear();
    }

    @Rule
    public final WireMockRule wireMock = new WireMockRule(options().dynamicPort(), true);

    @Test
    public void test_proxy() throws Exception {
        // Invalid requests
        assertResponse("/statuspage/v1/", "{\"error-code\":\"NOT_FOUND\",\"message\":\"Nothing at path '/statuspage/v1'\"}", 404);
        assertResponse("/statuspage/v1/invalid", "{\"error-code\":\"BAD_REQUEST\",\"message\":\"Invalid resource: 'invalid'\"}", 400);
        assertResponse(Request.Method.POST, "/statuspage/v1/invalid", "{\"error-code\":\"METHOD_NOT_ALLOWED\",\"message\":\"Method 'POST' is not supported\"}", 405);

        // Mock responses from StatusPage
        wireMock.stubFor(get(urlEqualTo("/api/v2/incidents.json?api_key=secret"))
                                 .willReturn(okJson("{\"incidents\":[]}")));
        wireMock.stubFor(get(urlEqualTo("/api/v2/scheduled-maintenances.json?api_key=secret"))
                                 .willReturn(okJson("{\"scheduled_maintenances\":[]}")));

        assertResponse("/statuspage/v1/incidents", "{\"incidents\":[]}", 200);
        assertResponse("/statuspage/v1/scheduled-maintenances", "{\"scheduled_maintenances\":[]}", 200);
    }

    private void assertResponse(String path, String expectedResponse, int expectedStatusCode) throws IOException {
        assertResponse(Request.Method.GET, path, expectedResponse, expectedStatusCode);
    }

    private void assertResponse(Request.Method method, String path, String expectedResponse, int expectedStatusCode) throws IOException {
        Response response = container.handleRequest(new Request("http://localhost:8080" + path, new byte[0], method));
        assertEquals(expectedResponse, response.getBodyAsString());
        assertEquals("Status code", expectedStatusCode, response.getStatus());
        assertEquals("application/json; charset=UTF-8", response.getHeaders().getFirst("Content-Type"));
    }

    private MockSecretStore secretStore() {
        return (MockSecretStore) container.components().getComponent(MockSecretStore.class.getName());
    }

    private String servicesXml() {
        String statusPageApiUrl = "http://127.0.0.1:" + wireMock.port();
        return "<jdisc version='1.0'>\n" +
               "  <config name='vespa.hosted.controller.statuspage.config.statuspage'>\n" +
               "    <apiUrl>" + statusPageApiUrl + "</apiUrl>\n" +
               "  </config>\n" +
               "  <component id='com.yahoo.vespa.hosted.controller.MockSecretStore'/>\n" +
               "  <handler id='com.yahoo.vespa.hosted.controller.restapi.statuspage.StatusPageProxyHandler'>\n" +
               "    <binding>http://*/statuspage/v1/*</binding>\n" +
               "  </handler>\n" +
               "  <http>\n" +
               "    <server id='default' port='8080'/>\n" +
               "  </http>\n" +
               "</jdisc>";
    }

}
