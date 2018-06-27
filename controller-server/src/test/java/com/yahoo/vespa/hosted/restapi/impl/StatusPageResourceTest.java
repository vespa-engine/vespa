// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.restapi.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yahoo.container.jdisc.secretstore.SecretStore;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.UriBuilder;
import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * @author frodelu
 */
public class StatusPageResourceTest {

    private StatusPageResource statusPage;

    @Before
    public void setup() throws IOException {

        Client mockClient = Mockito.mock(Client.class);
        WebTarget mockTarget = Mockito.mock(WebTarget.class);
        Invocation.Builder mockRequest = Mockito.mock(Invocation.Builder.class);
        SecretStore secretStore = Mockito.mock(SecretStore.class);

        Mockito.when(mockClient.target(Mockito.any(UriBuilder.class))).thenReturn(mockTarget);
        Mockito.when(mockTarget.request()).thenReturn(mockRequest);
        Mockito.when(mockRequest.get(JsonNode.class)).thenReturn(
            new ObjectMapper().readTree("{\"page\":{\"name\":\"Vespa\"}}"));
        Mockito.when(secretStore.getSecret(Mockito.any(String.class))).thenReturn("testpage:testkey");

        statusPage = new StatusPageResource(secretStore, mockClient);
    }


    @Test
    public void default_url() {
        UriBuilder uri = statusPage.statusPageURL("incidents", null);
        assertNotNull("URI not initialized", uri);
        assertEquals("https://testpage.statuspage.io/api/v2/incidents.json?api_key=testkey", uri.toTemplate());
    }

    @Test
    public void url_with_since_param() {
        UriBuilder uri = statusPage.statusPageURL("incidents", "2015-01-01T00:00+00:00");
        assertNotNull("URI not initialized", uri);
        assertEquals("https://testpage.statuspage.io/api/v2/incidents.json?api_key=testkey&since=2015-01-01T00%3A00%2B00%3A00", uri.toTemplate());
    }

    @Test
    public void valid_status_page() {
        JsonNode result = statusPage.statusPage("incidents", null);
        assertNotNull("No result from StatusPage.io", result);
        assertEquals("Vespa", result.get("page").get("name").asText());
    }
}
