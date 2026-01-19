// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.embedding;

import ai.vespa.embedding.config.VoyageAiEmbedderConfig;
import ai.vespa.secret.Secrets;
import com.yahoo.language.process.Embedder;
import com.yahoo.language.process.InvocationContext;
import com.yahoo.language.process.TimeoutException;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for VoyageAI embedder using MockWebServer to simulate API responses.
 */
public class VoyageAIEmbedderTest {

    private MockWebServer mockServer;
    private VoyageAIEmbedder embedder;
    private Embedder.Runtime runtime;

    @BeforeEach
    public void setUp() throws IOException {
        mockServer = new MockWebServer();
        mockServer.start();
        runtime = Embedder.Runtime.testInstance();
    }

    @AfterEach
    public void tearDown() throws IOException {
        if (embedder != null) {
            embedder.deconstruct();
        }
        mockServer.shutdown();
    }

    @Test
    public void testSuccessfulEmbedding() throws Exception {
        // Mock successful API response
        String responseJson = createSuccessResponse(1024);
        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(responseJson));

        embedder = createEmbedder();

        // Test embedding
        TensorType targetType = TensorType.fromSpec("tensor<float>(d0[1024])");
        Embedder.Context context = new Embedder.Context("test-embedder");
        Tensor result = embedder.embed("Hello, world!", context, targetType);

        // Verify
        assertNotNull(result);
        assertEquals(1024, result.size());
        assertEquals(targetType, result.type());

        // Verify API request
        RecordedRequest request = mockServer.takeRequest();
        assertEquals("POST", request.getMethod());
        assertEquals("/v1/embeddings", request.getPath());
        assertTrue(request.getHeader("Authorization").startsWith("Bearer "));
        assertTrue(request.getBody().readUtf8().contains("\"model\":\"voyage-3\""));
    }

    @Test
    public void testCaching() throws Exception {
        // Mock single API response
        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(createSuccessResponse(1024)));

        embedder = createEmbedder();
        TensorType targetType = TensorType.fromSpec("tensor<float>(d0[1024])");
        Embedder.Context context = new Embedder.Context("test-embedder");

        // First call - hits API
        Tensor result1 = embedder.embed("test text", context, targetType);
        assertEquals(1, mockServer.getRequestCount());

        // Second call with same text - should use cache
        Tensor result2 = embedder.embed("test text", context, targetType);
        assertEquals(1, mockServer.getRequestCount()); // Still only 1 request

        // Verify results are the same
        assertEquals(result1, result2);
    }

    @Test
    public void testDifferentTextsNotCached() throws Exception {
        // Mock two API responses
        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody(createSuccessResponse(1024)));
        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody(createSuccessResponse(1024)));

        embedder = createEmbedder();
        TensorType targetType = TensorType.fromSpec("tensor<float>(d0[1024])");
        Embedder.Context context = new Embedder.Context("test-embedder");

        // Two different texts
        embedder.embed("text one", context, targetType);
        embedder.embed("text two", context, targetType);

        // Should make 2 API calls
        assertEquals(2, mockServer.getRequestCount());
    }

    @Test
    public void testThrowsOverloadExceptionOn429() {
        // Mock 429 response
        mockServer.enqueue(new MockResponse()
                .setResponseCode(429)
                .setBody("{\"error\":\"rate_limit_exceeded\"}"));

        embedder = createEmbedder();
        TensorType targetType = TensorType.fromSpec("tensor<float>(d0[1024])");
        Embedder.Context context = new Embedder.Context("test-embedder");

        // Should throw OverloadException immediately
        var exception = assertThrows(
            com.yahoo.language.process.OverloadException.class,
            () -> embedder.embed("test", context, targetType)
        );

        assertEquals("VoyageAI API rate limited (429)", exception.getMessage());

        // Verify only 1 request was made (no retries on 429)
        assertEquals(1, mockServer.getRequestCount());
    }

    @Test
    public void testThrowsTimeoutExceptionOnSocketTimeout() {
        // Configure mock server with slow response to trigger socket timeout
        mockServer.enqueue(new MockResponse()
                .setBodyDelay(1, TimeUnit.SECONDS)
                .setBody("{\"data\":[{\"embedding\":[0.1,0.2,0.3,0.4]}]}"));

        // Create embedder with 100ms timeout to trigger timeout quickly
        VoyageAiEmbedderConfig.Builder configBuilder = new VoyageAiEmbedderConfig.Builder();
        configBuilder.apiKeySecretRef("test_key");
        configBuilder.endpoint(mockServer.url("/v1/embeddings").toString());
        configBuilder.model("voyage-3");
        configBuilder.timeout(100); // 100ms timeout
        embedder = new VoyageAIEmbedder(configBuilder.build(), runtime, createMockSecrets());

        TensorType targetType = TensorType.fromSpec("tensor<float>(d0[1024])");
        Embedder.Context context = new Embedder.Context("test-embedder");

        var exception = assertThrows(TimeoutException.class, () -> embedder.embed("test", context, targetType));

        var message = exception.getMessage();
        assertTrue(message.contains("VoyageAI API call timed out after"), "Expected message to contain 'VoyageAI API call timed out after', got: " + message);
        assertTrue(message.contains("ms"), "Expected message to contain 'ms', got: " + message);

        assertNotNull(exception.getCause());
        assertTrue(exception.getCause() instanceof java.io.InterruptedIOException
                || exception.getCause() instanceof java.net.SocketTimeoutException);

        // Verify only 1 request was made (no retries on timeout)
        assertEquals(1, mockServer.getRequestCount());
    }

    @Test
    public void testThrowsTimeoutExceptionOnExpiredDeadline() {
        embedder = createEmbedder();
        TensorType targetType = TensorType.fromSpec("tensor<float>(d0[1024])");

        // Create context with already-expired deadline
        var context = new Embedder.Context("test-embedder")
                .setDeadline(com.yahoo.language.process.InvocationContext.Deadline.of(
                        java.time.Instant.now().minusSeconds(1)));

        // Should throw TimeoutException before making HTTP request
        var exception = assertThrows(
                TimeoutException.class,
                () -> embedder.embed("test", context, targetType)
        );

        assertEquals("Request deadline exceeded before VoyageAI API call", exception.getMessage());

        // No HTTP request should be made
        assertEquals(0, mockServer.getRequestCount());
    }

    @Test
    public void testMaxRetriesExceeded() {
        // Create embedder with hardcoded maxRetries=3
        VoyageAiEmbedderConfig.Builder configBuilder = new VoyageAiEmbedderConfig.Builder();
        configBuilder.apiKeySecretRef("test_key");
        configBuilder.endpoint(mockServer.url("/v1/embeddings").toString());
        configBuilder.model("voyage-3");

        VoyageAIEmbedder embedder = new VoyageAIEmbedder(
                configBuilder.build(),
                runtime,
                createMockSecrets()
        );

        // Mock server error responses - more than maxRetries
        for (int i = 0; i < 10; i++) {
            mockServer.enqueue(new MockResponse()
                    .setResponseCode(500)
                    .setBody("{\"error\":\"internal_server_error\"}"));
        }

        TensorType targetType = TensorType.fromSpec("tensor<float>(d0[1024])");
        Embedder.Context context = new Embedder.Context("test-embedder");

        // Should fail after exhausting retries
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            embedder.embed("test", context, targetType);
        });
        assertEquals("VoyageAI API call failed: Max retries exceeded for VoyageAI API (3). Last response: 500 - {\"error\":\"internal_server_error\"}", exception.getMessage());

        embedder.deconstruct();
    }

    @Test
    public void testDeadlineTimeout() {
        var configBuilder = new VoyageAiEmbedderConfig.Builder()
                .apiKeySecretRef("test_key")
                .endpoint(mockServer.url("/v1/embeddings").toString())
                .model("voyage-3")
                .maxRetries(100);
        var embedder = new VoyageAIEmbedder(configBuilder.build(), runtime, createMockSecrets());

        for (int i = 0; i < 100; i++) {
            mockServer.enqueue(new MockResponse()
                    .setResponseCode(500)
                    .setBody("{\"error\":\"internal_server_error\"}"));
        }

        var targetType = TensorType.fromSpec("tensor<float>(d0[1024])");

        var context = new Embedder.Context("test-embedder")
                .setDeadline(InvocationContext.Deadline.of(Duration.ofMillis(500)));

        var exception = assertThrows(TimeoutException.class,
                () -> embedder.embed("test", context, targetType));

        String message = exception.getMessage();
        assertTrue(message.contains("VoyageAI API call timed out after"), "Expected message to contain 'VoyageAI API call timed out after', got: " + message);
        assertTrue(message.contains("ms"), "Expected message to contain 'ms', got: " + message);
        embedder.deconstruct();
    }

    @Test
    public void testMaxRetriesSafetyLimit() {
        // Create embedder with hardcoded maxRetries=3
        VoyageAiEmbedderConfig.Builder configBuilder = new VoyageAiEmbedderConfig.Builder();
        configBuilder.apiKeySecretRef("test_key");
        configBuilder.endpoint(mockServer.url("/v1/embeddings").toString());
        configBuilder.model("voyage-3");

        VoyageAIEmbedder embedder = new VoyageAIEmbedder(
                configBuilder.build(),
                runtime,
                createMockSecrets()
        );

        // Mock 5 server error responses (max retries is 3, so 1 + 3 retries = 4 total attempts)
        for (int i = 0; i < 5; i++) {
            mockServer.enqueue(new MockResponse()
                    .setResponseCode(503)
                    .setBody("{\"error\":\"service_unavailable\"}"));
        }

        TensorType targetType = TensorType.fromSpec("tensor<float>(d0[1024])");
        Embedder.Context context = new Embedder.Context("test-embedder");

        // Should fail after max retries
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            embedder.embed("test", context, targetType);
        });
        assertEquals("VoyageAI API call failed: Max retries exceeded for VoyageAI API (3). Last response: 503 - {\"error\":\"service_unavailable\"}", exception.getMessage());

        embedder.deconstruct();
    }

    @Test
    public void testAuthenticationError() {
        mockServer.enqueue(new MockResponse()
                .setResponseCode(401)
                .setBody("{\"error\":\"invalid_api_key\"}"));

        embedder = createEmbedder();
        TensorType targetType = TensorType.fromSpec("tensor<float>(d0[1024])");
        Embedder.Context context = new Embedder.Context("test-embedder");

        // Should fail with authentication error
        RuntimeException exception = assertThrows(RuntimeException.class, () -> embedder.embed("test", context, targetType));
        assertTrue(exception.getMessage().contains("authentication"));
    }

    @Test
    public void testDimensionMismatch() {
        // Mock response with 512 dimensions
        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody(createSuccessResponse(512)));

        embedder = createEmbedder();

        // Request 1024 dimensions but API returns 512
        TensorType targetType = TensorType.fromSpec("tensor<float>(d0[1024])");
        Embedder.Context context = new Embedder.Context("test-embedder");

        // Should fail with dimension mismatch error
        RuntimeException exception = assertThrows(RuntimeException.class, () -> embedder.embed("test", context, targetType));
        assertTrue(exception.getMessage().contains("dimension"));
    }

    @Test
    public void testInvalidTensorType() {
        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody(createSuccessResponse(1024)));

        embedder = createEmbedder();

        // 2D tensor type (invalid for embeddings)
        TensorType invalidType = TensorType.fromSpec("tensor<float>(d0[32],d1[32])");
        Embedder.Context context = new Embedder.Context("test-embedder");

        // Should fail with validation error
        assertThrows(IllegalArgumentException.class, () -> embedder.embed("test", context, invalidType));
    }

    @Test
    public void testMissingApiKeySecret() {
        VoyageAiEmbedderConfig.Builder configBuilder = new VoyageAiEmbedderConfig.Builder();
        configBuilder.apiKeySecretRef(""); // Empty secret name
        configBuilder.endpoint(mockServer.url("/v1/embeddings").toString());
        configBuilder.model("voyage-3");

        // Should fail during construction
        assertThrows(IllegalArgumentException.class, () -> {
            new VoyageAIEmbedder(configBuilder.build(), runtime, createMockSecrets());
        });
    }

    @Test
    public void testServerError() {
        mockServer.enqueue(new MockResponse()
                .setResponseCode(500)
                .setBody("{\"error\":\"internal_server_error\"}"));
        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody(createSuccessResponse(1024)));

        embedder = createEmbedder();
        TensorType targetType = TensorType.fromSpec("tensor<float>(d0[1024])");
        Embedder.Context context = new Embedder.Context("test");

        // Should retry on 500 error
        Tensor result = embedder.embed("test", context, targetType);
        assertNotNull(result);
        assertEquals(2, mockServer.getRequestCount()); // 1 failed + 1 success
    }

    @Test
    public void testInputTypeInRequest() throws Exception {
        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody(createSuccessResponse(1024)));

        embedder = createEmbedder();
        TensorType targetType = TensorType.fromSpec("tensor<float>(d0[1024])");
        Embedder.Context context = new Embedder.Context("test-embedder");

        embedder.embed("test", context, targetType);

        // Verify request contains input_type
        RecordedRequest request = mockServer.takeRequest();
        String body = request.getBody().readUtf8();
        assertTrue(body.contains("\"input_type\":\"document\"")); // Default is document
    }

    @Test
    public void testUnsupportedEmbedMethod() {
        embedder = createEmbedder();
        Embedder.Context context = new Embedder.Context("test-embedder");

        // Should throw UnsupportedOperationException for List<Integer> embed method
        UnsupportedOperationException exception = assertThrows(UnsupportedOperationException.class,
            () -> embedder.embed("test", context));
        assertTrue(exception.getMessage().contains("only supports embed() with TensorType"));
    }

    @Test
    public void testInvalidJsonResponse() {
        // Return invalid JSON that can't be parsed
        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{ this is not valid json }"));

        embedder = createEmbedder();
        TensorType targetType = TensorType.fromSpec("tensor<float>(d0[1024])");
        Embedder.Context context = new Embedder.Context("test-embedder");

        RuntimeException exception = assertThrows(RuntimeException.class, () -> embedder.embed("test", context, targetType));
        assertEquals("VoyageAI API call failed: Unexpected character ('t' (code 116)): was expecting double-quote to start field name\n" +
                " at [Source: REDACTED (`StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION` disabled); line: 1, column: 3]", exception.getMessage());
    }

    // ===== Helper Methods =====

    private VoyageAIEmbedder createEmbedder() {
        VoyageAiEmbedderConfig.Builder configBuilder = new VoyageAiEmbedderConfig.Builder();
        configBuilder.apiKeySecretRef("test_key");
        configBuilder.endpoint(mockServer.url("/v1/embeddings").toString());
        configBuilder.model("voyage-3");

        return new VoyageAIEmbedder(configBuilder.build(), runtime, createMockSecrets());
    }

    private Secrets createMockSecrets() {
        return key -> {
            if ("test_key".equals(key)) {
                return () -> "test-api-key-12345";
            }
            return null;
        };
    }

    private String createSuccessResponse(int dimensions) {
        StringBuilder embedding = new StringBuilder("[");
        for (int i = 0; i < dimensions; i++) {
            if (i > 0) embedding.append(",");
            // Create deterministic values for testing
            embedding.append(String.format("%.6f", Math.sin(i * 0.1)));
        }
        embedding.append("]");

        return String.format("""
                {
                  "object": "list",
                  "data": [
                    {
                      "object": "embedding",
                      "embedding": %s,
                      "index": 0
                    }
                  ],
                  "model": "voyage-3",
                  "usage": {
                    "total_tokens": 10
                  }
                }
                """, embedding);
    }
}
