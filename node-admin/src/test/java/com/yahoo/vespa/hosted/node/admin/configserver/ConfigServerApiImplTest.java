// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.configserver;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.http.HttpVersion;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.entity.BasicHttpEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.message.BasicStatusLine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Basic testing of retry logic.
 *
 * @author dybis
 */
public class ConfigServerApiImplTest {

    private static final int FAIL_RETURN_CODE = 100000;
    private static final int TIMEOUT_RETURN_CODE = 100001;

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TestPojo {
        @JsonProperty("foo")
        String foo;
        @JsonProperty("error-code")
        Integer errorCode;
    }

    private final String uri1 = "http://host1:666";
    private final String uri2 = "http://host2:666";
    private final List<URI> configServers = List.of(URI.create(uri1), URI.create(uri2));
    private final StringBuilder mockLog = new StringBuilder();

    private ConfigServerApiImpl configServerApi;
    private int mockReturnCode = 200;

    @BeforeEach
    public void initExecutor() throws IOException {
        CloseableHttpClient httpMock = mock(CloseableHttpClient.class);
        when(httpMock.execute(any())).thenAnswer(invocationOnMock -> {
            HttpGet get = (HttpGet) invocationOnMock.getArguments()[0];
            mockLog.append(get.getMethod()).append(" ").append(get.getURI()).append("  ");

            switch (mockReturnCode) {
                case FAIL_RETURN_CODE -> throw new RuntimeException("FAIL");
                case TIMEOUT_RETURN_CODE -> throw new SocketTimeoutException("read timed out");
            }

            BasicStatusLine statusLine = new BasicStatusLine(HttpVersion.HTTP_1_1, mockReturnCode, null);
            BasicHttpEntity entity = new BasicHttpEntity();
            String returnMessage = "{\"foo\":\"bar\", \"no\":3, \"error-code\": " + mockReturnCode + "}";
            InputStream stream = new ByteArrayInputStream(returnMessage.getBytes(StandardCharsets.UTF_8));
            entity.setContent(stream);

            CloseableHttpResponse response = mock(CloseableHttpResponse.class);
            when(response.getEntity()).thenReturn(entity);
            when(response.getStatusLine()).thenReturn(statusLine);

            return response;
        });
        configServerApi = ConfigServerApiImpl.createForTestingWithClient(configServers, httpMock);
    }

    @Test
    void testBasicParsingSingleServer() {
        TestPojo answer = configServerApi.get("/path", TestPojo.class);
        assertEquals(answer.foo, "bar");
        assertLogStringContainsGETForAHost();
    }

    @Test
    void testBasicFailure() {
        assertThrows(HttpException.class, () -> {
            // Server is returning 400, no retries.
            mockReturnCode = 400;

            TestPojo testPojo = configServerApi.get("/path", TestPojo.class);
            assertEquals(testPojo.errorCode.intValue(), mockReturnCode);
            assertLogStringContainsGETForAHost();
        });
    }

    @Test
    void testBasicSuccessWithNoRetries() {
        // Server is returning 201, no retries.
        mockReturnCode = 201;

        TestPojo testPojo = configServerApi.get("/path", TestPojo.class);
        assertEquals(testPojo.errorCode.intValue(), mockReturnCode);
        assertLogStringContainsGETForAHost();
    }

    @Test
    void testBasicSuccessWithCustomTimeouts() {
        mockReturnCode = TIMEOUT_RETURN_CODE;

        var params = new ConfigServerApi.Params<TestPojo>();
        params.setConnectionTimeout(Duration.ofSeconds(3));

        try {
            configServerApi.get("/path", TestPojo.class, params);
            fail();
        } catch (ConnectionException e) {
            assertNotNull(e.getCause());
            assertEquals("read timed out", e.getCause().getMessage());
        }
    }

    @Test
    void testRetries() {
        // Client is throwing exception, should be retries.
        mockReturnCode = FAIL_RETURN_CODE;
        try {
            configServerApi.get("/path", TestPojo.class);
            fail("Expected failure");
        } catch (Exception e) {
            // ignore
        }

        List<String> log = List.of(mockLog.toString().split("  "));
        assertTrue(log.containsAll(List.of("GET http://host1:666/path", "GET http://host2:666/path")));
    }

    @Test
    void testNoRetriesOnBadHttpResponseCode() {
        // Client is throwing exception, should be retries.
        mockReturnCode = 503;
        try {
            configServerApi.get("/path", TestPojo.class);
            fail("Expected failure");
        } catch (Exception e) {
            // ignore
        }

        assertLogStringContainsGETForAHost();
    }

    @Test
    void testForbidden() {
        mockReturnCode = 403;
        try {
            configServerApi.get("/path", TestPojo.class);
            fail("Expected exception");
        } catch (HttpException.ForbiddenException e) {
            // ignore
        }
        assertLogStringContainsGETForAHost();
    }

    @Test
    void testNotFound() {
        // Server is returning 404, special exception is thrown.
        mockReturnCode = 404;
        try {
            configServerApi.get("/path", TestPojo.class);
            fail("Expected exception");
        } catch (HttpException.NotFoundException e) {
            // ignore
        }
        assertLogStringContainsGETForAHost();
    }

    @Test
    void testConflict() {
        // Server is returning 409, no exception is thrown.
        mockReturnCode = 409;
        configServerApi.get("/path", TestPojo.class);
        assertLogStringContainsGETForAHost();
    }

    private void assertLogStringContainsGETForAHost() {
        String logString = mockLog.toString();
        assertTrue((logString.equals("GET http://host1:666/path  ") || logString.equals("GET http://host2:666/path  ")),
                   "log does not contain expected entries:" + logString);
    }
}
