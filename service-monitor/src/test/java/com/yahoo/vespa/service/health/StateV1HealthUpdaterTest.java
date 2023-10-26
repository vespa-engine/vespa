// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.health;

import com.yahoo.vespa.applicationmodel.ServiceStatus;
import com.yahoo.vespa.applicationmodel.ServiceStatusInfo;
import org.apache.http.HttpEntity;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.impl.client.CloseableHttpClient;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.URL;
import java.util.function.Function;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class StateV1HealthUpdaterTest {
    private URL url;

    @Before
    public void setUp() throws Exception{
        url = new URL("http://host.com:19071");
    }

    @Test
    public void successfulRequestResponse() throws IOException {
        ServiceStatusInfo info = getServiceStatusInfoFromJsonResponse("{\n" +
                "    \"metrics\": {\n" +
                "        \"snapshot\": {\n" +
                "            \"from\": 1.528789829249E9,\n" +
                "            \"to\": 1.528789889249E9\n" +
                "        }\n" +
                "    },\n" +
                "    \"status\": {\"code\": \"up\"},\n" +
                "    \"time\": 1528789889364\n" +
                "}");
        assertEquals(ServiceStatus.UP, info.serviceStatus());
        assertNull(info.errorOrNull());
    }

    @Test
    public void notUpResponse() throws IOException {
        ServiceStatusInfo info = getServiceStatusInfoFromJsonResponse("{\n" +
                "    \"metrics\": {\n" +
                "        \"snapshot\": {\n" +
                "            \"from\": 1.528789829249E9,\n" +
                "            \"to\": 1.528789889249E9\n" +
                "        }\n" +
                "    },\n" +
                "    \"status\": {\"code\": \"initializing\"},\n" +
                "    \"time\": 1528789889364\n" +
                "}");
        assertEquals(ServiceStatus.DOWN, info.serviceStatus());
        assertEquals("Bad health status code 'initializing'", info.errorOrNull());
    }

    @Test
    public void noCodeInResponse() throws IOException {
        ServiceStatusInfo info = getServiceStatusInfoFromJsonResponse("{\n" +
                "    \"metrics\": {\n" +
                "        \"snapshot\": {\n" +
                "            \"from\": 1.528789829249E9,\n" +
                "            \"to\": 1.528789889249E9\n" +
                "        }\n" +
                "    },\n" +
                "    \"status\": {\"foo\": \"bar\"},\n" +
                "    \"time\": 1528789889364\n" +
                "}");
        assertEquals(ServiceStatus.DOWN, info.serviceStatus());
        assertEquals("Bad health status code 'down'", info.errorOrNull());
    }

    @Test
    public void noStatusInResponse() throws IOException {
        ServiceStatusInfo info = getServiceStatusInfoFromJsonResponse("{\n" +
                "    \"metrics\": {\n" +
                "        \"snapshot\": {\n" +
                "            \"from\": 1.528789829249E9,\n" +
                "            \"to\": 1.528789889249E9\n" +
                "        }\n" +
                "    },\n" +
                "    \"time\": 1528789889364\n" +
                "}");
        assertEquals(ServiceStatus.DOWN, info.serviceStatus());
        assertEquals("Bad health status code 'down'", info.errorOrNull());
    }

    @Test
    public void badJson() throws IOException {
        ServiceStatusInfo info = getServiceStatusInfoFromJsonResponse("} foo bar");
        assertEquals(ServiceStatus.DOWN, info.serviceStatus());
        assertTrue(info.errorOrNull().startsWith("Exception: Unexpected close marker '}': "));
    }

    private ServiceStatusInfo getServiceStatusInfoFromJsonResponse(String content)
            throws IOException {
        CloseableHttpClient client = mock(CloseableHttpClient.class);

        CloseableHttpResponse response = mock(CloseableHttpResponse.class);
        when(client.execute(any())).thenReturn(response);

        StatusLine statusLine = mock(StatusLine.class);
        when(response.getStatusLine()).thenReturn(statusLine);

        when(statusLine.getStatusCode()).thenReturn(200);

        HttpEntity httpEntity = mock(HttpEntity.class);
        when(response.getEntity()).thenReturn(httpEntity);

        try (StateV1HealthUpdater updater = makeUpdater(client, entry -> content)) {
            when(httpEntity.getContentLength()).thenReturn((long) content.length());
            updater.run();
            return updater.getServiceStatusInfo();
        }
    }

    @Test
    public void testRequestException() throws IOException {
        CloseableHttpClient client = mock(CloseableHttpClient.class);

        when(client.execute(any())).thenThrow(new ConnectTimeoutException("exception string"));

        try (StateV1HealthUpdater updater = makeUpdater(client, entry -> "")) {
            updater.run();
            ServiceStatusInfo info = updater.getServiceStatusInfo();
            assertEquals(ServiceStatus.DOWN, info.serviceStatus());
            assertEquals("Exception: exception string", info.errorOrNull());
        }
    }

    @Test
    public void testBadHttpResponseCode()
            throws IOException {
        CloseableHttpClient client = mock(CloseableHttpClient.class);

        CloseableHttpResponse response = mock(CloseableHttpResponse.class);
        when(client.execute(any())).thenReturn(response);

        StatusLine statusLine = mock(StatusLine.class);
        when(response.getStatusLine()).thenReturn(statusLine);

        when(statusLine.getStatusCode()).thenReturn(500);

        HttpEntity httpEntity = mock(HttpEntity.class);
        when(response.getEntity()).thenReturn(httpEntity);

        String content = "{}";
        try (HealthUpdater updater = makeUpdater(client, entry -> content)) {
            when(httpEntity.getContentLength()).thenReturn((long) content.length());
            updater.run();
            ServiceStatusInfo info = updater.getServiceStatusInfo();
            assertEquals(ServiceStatus.DOWN, info.serviceStatus());
            assertEquals("Bad HTTP response status code 500", info.errorOrNull());
        }
    }

    private StateV1HealthUpdater makeUpdater(CloseableHttpClient client, Function<HttpEntity, String> getContentFunction) {
        ApacheHttpClient apacheHttpClient = new ApacheHttpClient(url, client);
        StateV1HealthClient healthClient = new StateV1HealthClient(apacheHttpClient, getContentFunction);
        return new StateV1HealthUpdater(url.toString(), healthClient);
    }
}
