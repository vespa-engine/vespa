// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.embedding;

import ai.vespa.secret.Secret;
import ai.vespa.secret.Secrets;
import ai.vespa.embedding.config.VoyageAiEmbedderConfig;
import com.yahoo.language.process.Embedder;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorAddress;
import com.yahoo.tensor.TensorType;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

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
    public void testRateLimitRetry() throws Exception {
        // First request: rate limited
        mockServer.enqueue(new MockResponse()
                .setResponseCode(429)
                .setHeader("Retry-After", "1")
                .setBody("{\"error\":\"rate_limit_exceeded\"}"));

        // Second request: success
        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody(createSuccessResponse(1024)));

        embedder = createEmbedder();
        TensorType targetType = TensorType.fromSpec("tensor<float>(d0[1024])");
        Embedder.Context context = new Embedder.Context("test-embedder");

        // Should retry and succeed
        Tensor result = embedder.embed("test", context, targetType);
        assertNotNull(result);

        // Verify 2 requests were made (1 failed, 1 succeeded)
        assertEquals(2, mockServer.getRequestCount());
    }

    @Test
    public void testTimeoutExceeded() {
        // Create embedder with very short timeout
        VoyageAiEmbedderConfig.Builder configBuilder = new VoyageAiEmbedderConfig.Builder();
        configBuilder.apiKeySecretRef("test_key");
        configBuilder.endpoint(mockServer.url("/v1/embeddings").toString());
        configBuilder.model("voyage-3");
        configBuilder.timeout(2000); // 2 second timeout
        configBuilder.maxRetries(100); // High retry count, but timeout should hit first

        VoyageAIEmbedder shortTimeoutEmbedder = new VoyageAIEmbedder(
                configBuilder.build(),
                runtime,
                createMockSecrets()
        );

        // Mock multiple rate limit responses (each retry waits 1 second)
        for (int i = 0; i < 10; i++) {
            mockServer.enqueue(new MockResponse()
                    .setResponseCode(429)
                    .setBody("{\"error\":\"rate_limit_exceeded\"}"));
        }

        TensorType targetType = TensorType.fromSpec("tensor<float>(d0[1024])");
        Embedder.Context context = new Embedder.Context("test-embedder");

        // Should fail when timeout is reached
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            shortTimeoutEmbedder.embed("test", context, targetType);
        });
        assertTrue(exception.getMessage().contains("timeout") || exception.getMessage().contains("exceed"));

        shortTimeoutEmbedder.deconstruct();
    }

    @Test
    public void testMaxRetriesSafetyLimit() {
        // Create embedder with low maxRetries
        VoyageAiEmbedderConfig.Builder configBuilder = new VoyageAiEmbedderConfig.Builder();
        configBuilder.apiKeySecretRef("test_key");
        configBuilder.endpoint(mockServer.url("/v1/embeddings").toString());
        configBuilder.model("voyage-3");
        configBuilder.timeout(30000); // High timeout
        configBuilder.maxRetries(2); // Low retry count

        VoyageAIEmbedder lowRetryEmbedder = new VoyageAIEmbedder(
                configBuilder.build(),
                runtime,
                createMockSecrets()
        );

        // Mock 4 rate limit responses (max retries is 2)
        for (int i = 0; i < 4; i++) {
            mockServer.enqueue(new MockResponse()
                    .setResponseCode(429)
                    .setBody("{\"error\":\"rate_limit_exceeded\"}"));
        }

        TensorType targetType = TensorType.fromSpec("tensor<float>(d0[1024])");
        Embedder.Context context = new Embedder.Context("test-embedder");

        // Should fail after max retries
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            lowRetryEmbedder.embed("test", context, targetType);
        });
        assertTrue(exception.getMessage().contains("Max retries") || exception.getMessage().contains("exceeded"));

        lowRetryEmbedder.deconstruct();
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
    public void testNormalization() throws Exception {
        VoyageAiEmbedderConfig.Builder configBuilder = new VoyageAiEmbedderConfig.Builder();
        configBuilder.apiKeySecretRef("test_key");
        configBuilder.endpoint(mockServer.url("/v1/embeddings").toString());
        configBuilder.model("voyage-3");
        configBuilder.normalize(true); // Enable normalization

        VoyageAIEmbedder normalizingEmbedder = new VoyageAIEmbedder(
                configBuilder.build(),
                runtime,
                createMockSecrets()
        );

        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody(createSuccessResponse(128)));

        TensorType targetType = TensorType.fromSpec("tensor<float>(d0[128])");
        Embedder.Context context = new Embedder.Context("test");

        Tensor result = normalizingEmbedder.embed("test", context, targetType);

        // Verify tensor is normalized (L2 norm should be ~1.0)
        double sumSquares = 0.0;
        for (int i = 0; i < 128; i++) {
            double val = result.get(TensorAddress.of(i));
            sumSquares += val * val;
        }
        double norm = Math.sqrt(sumSquares);
        assertEquals(1.0, norm, 0.01); // Should be close to 1.0

        normalizingEmbedder.deconstruct();
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

        // Should fail with parse error
        RuntimeException exception = assertThrows(RuntimeException.class, () -> embedder.embed("test", context, targetType));
        assertTrue(exception.getMessage().contains("Failed to parse") ||
                   exception.getMessage().contains("parse"));
    }

    // ===== Helper Methods =====

    private VoyageAIEmbedder createEmbedder() {
        VoyageAiEmbedderConfig.Builder configBuilder = new VoyageAiEmbedderConfig.Builder();
        configBuilder.apiKeySecretRef("test_key");
        configBuilder.endpoint(mockServer.url("/v1/embeddings").toString());
        configBuilder.model("voyage-3");
        configBuilder.maxRetries(10);
        configBuilder.timeout(5000);

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
