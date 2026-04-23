// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.embedding;

import ai.vespa.embedding.config.HttpEmbedderConfig;
import com.yahoo.language.process.Embedder;
import com.yahoo.language.process.InvalidInputException;
import com.yahoo.language.process.InvocationContext;
import com.yahoo.language.process.OverloadException;
import com.yahoo.language.process.TimeoutException;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.SocketPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the HTTP transport behavior of {@link AbstractHttpEmbedder}: status-code-to-exception
 * mapping, timeouts, retries, and error-body parsing. Provider-specific embedder tests cover only
 * request building and response parsing; the shared HTTP behavior is verified here.
 *
 * @author bjorncs
 */
public class AbstractHttpEmbedderTest {

    private MockWebServer mockServer;
    private RecordingRuntime runtime;
    private AbstractHttpEmbedder embedder;

    @BeforeEach
    public void setUp() throws IOException {
        mockServer = new MockWebServer();
        mockServer.start();
        runtime = new RecordingRuntime();
        embedder = new AbstractHttpEmbedder() {};
    }

    @AfterEach
    public void tearDown() throws IOException {
        embedder.deconstruct();
        mockServer.shutdown();
    }

    @Test
    public void testReturnsBodyOn2xx() {
        mockServer.enqueue(new MockResponse().setResponseCode(200).setBody("hello"));
        assertEquals("hello", call());
    }

    @Test
    public void testThrowsOverloadExceptionOn429() {
        mockServer.enqueue(new MockResponse().setResponseCode(429).setBody("{\"error\":{\"message\":\"rate_limit\"}}"));
        var exception = assertThrows(OverloadException.class, this::call);
        assertEquals("Embedding API rate limited (429)", exception.getMessage());
        assertEquals(1, mockServer.getRequestCount());
    }

    @Test
    public void testThrowsAuthErrorOn401() {
        mockServer.enqueue(new MockResponse().setResponseCode(401).setBody("invalid_api_key"));
        var exception = assertThrows(RuntimeException.class, this::call);
        assertTrue(exception.getMessage().contains("authentication failed (401)"));
    }

    @Test
    public void testThrowsAuthErrorOn403() {
        mockServer.enqueue(new MockResponse().setResponseCode(403).setBody("forbidden"));
        var exception = assertThrows(RuntimeException.class, this::call);
        assertTrue(exception.getMessage().contains("authentication failed (403)"));
    }

    @Test
    public void testParsesOpenAIStyleErrorMessageOn400() {
        mockServer.enqueue(new MockResponse().setResponseCode(400).setBody("{\"error\":{\"message\":\"invalid input\"}}"));
        var exception = assertThrows(InvalidInputException.class, this::call);
        assertEquals("Embedding API bad request (400): invalid input", exception.getMessage());
    }

    @Test
    public void testParsesDetailStyleErrorMessageOn400() {
        mockServer.enqueue(new MockResponse().setResponseCode(400).setBody("{\"detail\":\"too many tokens\"}"));
        var exception = assertThrows(InvalidInputException.class, this::call);
        assertEquals("Embedding API bad request (400): too many tokens", exception.getMessage());
    }

    @Test
    public void testFallsBackToRawBodyOn400WhenParsingFails() {
        mockServer.enqueue(new MockResponse().setResponseCode(400).setBody("not json"));
        var exception = assertThrows(InvalidInputException.class, this::call);
        assertEquals("Embedding API bad request (400): not json", exception.getMessage());
    }

    @Test
    public void testThrowsGenericRuntimeExceptionOnOtherStatusCode() {
        mockServer.enqueue(new MockResponse().setResponseCode(418).setBody("teapot"));
        var exception = assertThrows(RuntimeException.class, this::call);
        assertEquals("Embedding API request failed with status 418: teapot", exception.getMessage());
    }

    @Test
    public void testThrowsTimeoutExceptionOnSocketTimeout() {
        mockServer.enqueue(new MockResponse().setBodyDelay(1, TimeUnit.SECONDS).setBody("{}"));
        var context = new Embedder.Context("test")
                .setDeadline(InvocationContext.Deadline.of(Duration.ofMillis(100)));

        var exception = assertThrows(TimeoutException.class,
                () -> embedder.doHttpRequest(endpointUrl(), "{}", Map.of(), context, runtime));
        assertTrue(exception.getMessage().contains("Embedding API call timed out after"));
        assertEquals(1, mockServer.getRequestCount());
    }

    @Test
    public void testThrowsTimeoutExceptionOnExpiredDeadline() {
        var context = new Embedder.Context("test")
                .setDeadline(InvocationContext.Deadline.of(Instant.now().minusSeconds(1)));

        var exception = assertThrows(TimeoutException.class,
                () -> embedder.doHttpRequest(endpointUrl(), "{}", Map.of(), context, runtime));
        assertEquals("Request deadline exceeded before embedding API call", exception.getMessage());
        assertEquals(0, mockServer.getRequestCount());
    }

    @Test
    public void testRetriesOnServerError() {
        mockServer.enqueue(new MockResponse().setResponseCode(500).setBody("fail"));
        mockServer.enqueue(new MockResponse().setResponseCode(200).setBody("ok"));

        assertEquals("ok", call());
        assertEquals(2, mockServer.getRequestCount());
    }

    @Test
    public void testRetriesOnIOException() {
        mockServer.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST));
        mockServer.enqueue(new MockResponse().setResponseCode(200).setBody("ok"));

        assertEquals("ok", call());
        assertEquals(2, mockServer.getRequestCount());
    }

    @Test
    public void testMaxRetriesExceededOnIOExceptionRethrows() {
        for (int i = 0; i < 10; i++) {
            mockServer.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST));
        }

        var exception = assertThrows(RuntimeException.class, this::call);
        assertTrue(exception.getMessage().startsWith("Embedding API call failed:"));
        assertEquals(List.of(0), runtime.sampledFailures);
        assertEquals(4, mockServer.getRequestCount()); // 1 initial + 3 retries
    }

    @Test
    public void testMaxRetriesExceededRecordsActualStatusCode() {
        for (int i = 0; i < 10; i++) {
            mockServer.enqueue(new MockResponse().setResponseCode(503).setBody("{\"error\":{\"message\":\"unavailable\"}}"));
        }

        var exception = assertThrows(RuntimeException.class, this::call);
        assertEquals("Embedding API request failed with status 503: {\"error\":{\"message\":\"unavailable\"}}",
                exception.getMessage());
        assertEquals(List.of(503), runtime.sampledFailures);
        assertEquals(4, mockServer.getRequestCount()); // 1 initial + 3 retries
    }

    @Test
    public void testForwardsHeadersToServer() throws Exception {
        mockServer.enqueue(new MockResponse().setResponseCode(200).setBody("ok"));

        embedder.doHttpRequest(endpointUrl(), "{\"k\":\"v\"}",
                Map.of("Authorization", "Bearer xyz", "X-Custom", "hi"),
                new Embedder.Context("test"), runtime);

        var request = mockServer.takeRequest();
        assertEquals("Bearer xyz", request.getHeader("Authorization"));
        assertEquals("hi", request.getHeader("X-Custom"));
        assertEquals("{\"k\":\"v\"}", request.getBody().readUtf8());
        assertEquals("application/json; charset=utf-8", request.getHeader("Content-Type"));
    }

    @Test
    public void testConstructorWithConfigHonorsMaxRetries() {
        var config = new HttpEmbedderConfig.Builder().maxRetries(1).build();
        var embedderWithConfig = new AbstractHttpEmbedder(config) {};
        try {
            for (int i = 0; i < 5; i++) {
                mockServer.enqueue(new MockResponse().setResponseCode(503).setBody("x"));
            }
            var exception = assertThrows(RuntimeException.class,
                    () -> embedderWithConfig.doHttpRequest(endpointUrl(), "{}", Map.of(),
                            new Embedder.Context("test"), runtime));
            assertEquals("Embedding API request failed with status 503: x", exception.getMessage());
            assertEquals(2, mockServer.getRequestCount()); // 1 initial + 1 retry
        } finally {
            embedderWithConfig.deconstruct();
        }
    }

    // ===== Helpers =====

    private String call() {
        return embedder.doHttpRequest(endpointUrl(), "{}", Map.of(), new Embedder.Context("test"), runtime);
    }

    private String endpointUrl() {
        return mockServer.url("/").toString();
    }

    private static class RecordingRuntime implements Embedder.Runtime {
        final List<Integer> sampledFailures = new ArrayList<>();

        @Override public void sampleEmbeddingLatency(double millis, Embedder.Context ctx) { }
        @Override public void sampleSequenceLength(long length, Embedder.Context ctx) { }
        @Override public void sampleRequestCount(Embedder.Context ctx) { }
        @Override public void sampleRequestFailure(Embedder.Context ctx, int statusCode) { sampledFailures.add(statusCode); }
    }
}
