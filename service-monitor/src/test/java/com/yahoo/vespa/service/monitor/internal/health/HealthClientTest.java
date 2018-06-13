// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.monitor.internal.health;

import com.yahoo.config.provision.HostName;
import com.yahoo.vespa.applicationmodel.ServiceStatus;
import org.apache.http.HttpEntity;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.impl.client.CloseableHttpClient;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class HealthClientTest {
    @Test
    public void successfulRequestResponse() throws IOException {
        HealthInfo info = getHealthInfoFromJsonResponse("{\n" +
                "    \"metrics\": {\n" +
                "        \"snapshot\": {\n" +
                "            \"from\": 1.528789829249E9,\n" +
                "            \"to\": 1.528789889249E9\n" +
                "        }\n" +
                "    },\n" +
                "    \"status\": {\"code\": \"up\"},\n" +
                "    \"time\": 1528789889364\n" +
                "}");
        assertTrue(info.isHealthy());
        assertEquals(ServiceStatus.UP, info.toServiceStatus());
    }

    @Test
    public void notUpResponse() throws IOException {
        HealthInfo info = getHealthInfoFromJsonResponse("{\n" +
                "    \"metrics\": {\n" +
                "        \"snapshot\": {\n" +
                "            \"from\": 1.528789829249E9,\n" +
                "            \"to\": 1.528789889249E9\n" +
                "        }\n" +
                "    },\n" +
                "    \"status\": {\"code\": \"initializing\"},\n" +
                "    \"time\": 1528789889364\n" +
                "}");
        assertFalse(info.isHealthy());
        assertEquals(ServiceStatus.DOWN, info.toServiceStatus());
        assertEquals("Bad health status code 'initializing'", info.toString());
    }

    @Test
    public void noCodeInResponse() throws IOException {
        HealthInfo info = getHealthInfoFromJsonResponse("{\n" +
                "    \"metrics\": {\n" +
                "        \"snapshot\": {\n" +
                "            \"from\": 1.528789829249E9,\n" +
                "            \"to\": 1.528789889249E9\n" +
                "        }\n" +
                "    },\n" +
                "    \"status\": {\"foo\": \"bar\"},\n" +
                "    \"time\": 1528789889364\n" +
                "}");
        assertFalse(info.isHealthy());
        assertEquals(ServiceStatus.DOWN, info.toServiceStatus());
        assertEquals("Bad health status code 'down'", info.toString());
    }

    @Test
    public void noStatusInResponse() throws IOException {
        HealthInfo info = getHealthInfoFromJsonResponse("{\n" +
                "    \"metrics\": {\n" +
                "        \"snapshot\": {\n" +
                "            \"from\": 1.528789829249E9,\n" +
                "            \"to\": 1.528789889249E9\n" +
                "        }\n" +
                "    },\n" +
                "    \"time\": 1528789889364\n" +
                "}");
        assertFalse(info.isHealthy());
        assertEquals(ServiceStatus.DOWN, info.toServiceStatus());
        assertEquals("Bad health status code 'down'", info.toString());
    }

    @Test
    public void badJson() throws IOException {
        HealthInfo info = getHealthInfoFromJsonResponse("} foo bar");
        assertFalse(info.isHealthy());
        assertEquals(ServiceStatus.DOWN, info.toServiceStatus());
        assertTrue(info.toString().startsWith("Exception: Unexpected close marker '}': "));
    }

    private HealthInfo getHealthInfoFromJsonResponse(String content)
            throws IOException {
        HealthEndpoint endpoint = HealthEndpoint.forHttp(HostName.from("host.com"), 19071);
        CloseableHttpClient client = mock(CloseableHttpClient.class);

        CloseableHttpResponse response = mock(CloseableHttpResponse.class);
        when(client.execute(any())).thenReturn(response);

        StatusLine statusLine = mock(StatusLine.class);
        when(response.getStatusLine()).thenReturn(statusLine);

        when(statusLine.getStatusCode()).thenReturn(200);

        HttpEntity httpEntity = mock(HttpEntity.class);
        when(response.getEntity()).thenReturn(httpEntity);

        try (HealthClient healthClient = new HealthClient(endpoint, () -> client, entry -> content)) {
            healthClient.start();

            when(httpEntity.getContentLength()).thenReturn((long) content.length());
            return healthClient.getHealthInfo();
        }
    }

    @Test
    public void testRequestException() throws IOException {
        HealthEndpoint endpoint = HealthEndpoint.forHttp(HostName.from("host.com"), 19071);
        CloseableHttpClient client = mock(CloseableHttpClient.class);

        when(client.execute(any())).thenThrow(new ConnectTimeoutException("exception string"));

        try (HealthClient healthClient = new HealthClient(endpoint, () -> client, entry -> "")) {
            healthClient.start();
            HealthInfo info = healthClient.getHealthInfo();
            assertFalse(info.isHealthy());
            assertEquals(ServiceStatus.DOWN, info.toServiceStatus());
            assertEquals("Exception: exception string", info.toString());
        }
    }

    @Test
    public void testBadHttpResponseCode()
            throws IOException {
        HealthEndpoint endpoint = HealthEndpoint.forHttp(HostName.from("host.com"), 19071);
        CloseableHttpClient client = mock(CloseableHttpClient.class);

        CloseableHttpResponse response = mock(CloseableHttpResponse.class);
        when(client.execute(any())).thenReturn(response);

        StatusLine statusLine = mock(StatusLine.class);
        when(response.getStatusLine()).thenReturn(statusLine);

        when(statusLine.getStatusCode()).thenReturn(500);

        HttpEntity httpEntity = mock(HttpEntity.class);
        when(response.getEntity()).thenReturn(httpEntity);

        String content = "{}";
        try (HealthClient healthClient = new HealthClient(endpoint, () -> client, entry -> content)) {
            healthClient.start();

            when(httpEntity.getContentLength()).thenReturn((long) content.length());
            HealthInfo info = healthClient.getHealthInfo();
            assertFalse(info.isHealthy());
            assertEquals(ServiceStatus.DOWN, info.toServiceStatus());
            assertEquals("Bad HTTP response status code 500", info.toString());
        }
    }
}