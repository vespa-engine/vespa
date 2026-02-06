// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.embedding;

import ai.vespa.embedding.config.VoyageAiEmbedderConfig;
import ai.vespa.secret.Secrets;
import com.yahoo.language.process.Embedder;
import com.yahoo.language.process.InvocationContext;
import com.yahoo.language.process.TimeoutException;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import com.yahoo.text.Text;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;

import static com.yahoo.text.Lowercase.toUpperCase;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for VoyageAI embedder using MockWebServer to simulate API responses.
 *
 * @author bjorncs
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
        configBuilder.dimensions(1024);
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
        configBuilder.dimensions(1024);

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
                .dimensions(1024)
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
        configBuilder.dimensions(1024);

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
    public void testAutoQuantizationWithFloatTensor() throws Exception {
        embedder = createEmbedder(1024, "auto");

        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(createFloatSuccessResponse(1024)));

        TensorType targetType = TensorType.fromSpec("tensor<float>(x[1024])");
        Embedder.Context context = new Embedder.Context("test-embedder");

        Tensor result = embedder.embed("test", context, targetType);

        assertNotNull(result);
        assertEquals(1024, result.size());
        assertEquals(TensorType.Value.FLOAT, result.type().valueType());

        // Verify request contains output_dtype=float and output_dimension=1024
        RecordedRequest request = mockServer.takeRequest();
        String body = request.getBody().readUtf8();
        assertTrue(body.contains("\"output_dtype\":\"float\""));
        assertTrue(body.contains("\"output_dimension\":1024"));
    }

    @Test
    public void testAutoQuantizationWithInt8Tensor() throws Exception {
        embedder = createEmbedder(1024, "auto");

        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(createInt8SuccessResponse(1024)));

        TensorType targetType = TensorType.fromSpec("tensor<int8>(x[1024])");
        Embedder.Context context = new Embedder.Context("test-embedder");

        Tensor result = embedder.embed("test", context, targetType);

        assertNotNull(result);
        assertEquals(1024, result.size());
        assertEquals(TensorType.Value.INT8, result.type().valueType());

        // Verify request contains output_dtype=int8
        RecordedRequest request = mockServer.takeRequest();
        String body = request.getBody().readUtf8();
        assertTrue(body.contains("\"output_dtype\":\"int8\""));
        assertTrue(body.contains("\"output_dimension\":1024"));
    }

    @Test
    public void testAutoQuantizationWithBinaryTensor() throws Exception {
        embedder = createEmbedder(1024, "auto");

        // Binary embedding has 1/8 dimension (1024/8 = 128)
        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(createBinarySuccessResponse(128)));

        TensorType targetType = TensorType.fromSpec("tensor<int8>(x[128])");
        Embedder.Context context = new Embedder.Context("test-embedder");

        Tensor result = embedder.embed("test", context, targetType);

        assertNotNull(result);
        assertEquals(128, result.size());
        assertEquals(TensorType.Value.INT8, result.type().valueType());

        // Verify request contains output_dtype=binary but output_dimension=1024 (full dimension)
        RecordedRequest request = mockServer.takeRequest();
        String body = request.getBody().readUtf8();
        assertTrue(body.contains("\"output_dtype\":\"binary\""));
        assertTrue(body.contains("\"output_dimension\":1024"));
    }

    @Test
    public void testExplicitFloatQuantizationValidation() {
        // Float quantization requires float tensor
        embedder = createEmbedder(1024, "float");

        TensorType int8Type = TensorType.fromSpec("tensor<int8>(x[1024])");
        Embedder.Context context = new Embedder.Context("test-embedder");

        var exception = assertThrows(IllegalArgumentException.class,
                () -> embedder.embed("test", context, int8Type));
        assertEquals("Quantization 'float' is incompatible with tensor type tensor<int8>(x[1024]).", exception.getMessage());
    }

    @Test
    public void testExplicitInt8QuantizationValidation() {
        // Int8 quantization requires int8 tensor
        embedder = createEmbedder(1024, "int8");

        TensorType floatType = TensorType.fromSpec("tensor<float>(x[1024])");
        Embedder.Context context = new Embedder.Context("test-embedder");

        var exception = assertThrows(IllegalArgumentException.class,
                () -> embedder.embed("test", context, floatType));
        assertEquals("Quantization 'int8' is incompatible with tensor type tensor<float>(x[1024]).", exception.getMessage());
    }

    @Test
    public void testExplicitBinaryQuantizationValidation() {
        // Binary quantization requires int8 tensor with dimension/8
        embedder = createEmbedder(1024, "binary");

        // Tensor has full dimension instead of 1/8
        var targetType = TensorType.fromSpec("tensor<int8>(x[1024])");
        var context = new Embedder.Context("test-embedder");

        var exception = assertThrows(IllegalArgumentException.class,
                () -> embedder.embed("test", context, targetType));
        assertEquals("Tensor dimension 1024 does not match required dimension 128.", exception.getMessage());
    }

    @Test
    public void testDimensionMismatchBetweenConfigAndTensorType() {
        embedder = createEmbedder(512, "auto");

        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(createFloatSuccessResponse(512)));

        // Tensor type has different dimension than config
        var targetType = TensorType.fromSpec("tensor<float>(d0[1024])");
        var context = new Embedder.Context("test-embedder");

        var exception = assertThrows(IllegalArgumentException.class, () -> embedder.embed("test", context, targetType));
        assertEquals("Tensor dimension 1024 does not match configured dimension 512.", exception.getMessage());
    }

    // ===== Helper Methods =====

    private VoyageAIEmbedder createEmbedder() {
        return createEmbedder(1024, "auto");
    }

    private VoyageAIEmbedder createEmbedder(int dimensions, String quantization) {
        VoyageAiEmbedderConfig.Builder configBuilder = new VoyageAiEmbedderConfig.Builder()
                .apiKeySecretRef("test_key")
                .endpoint(mockServer.url("/v1/embeddings").toString())
                .model("voyage-3")
                .dimensions(dimensions)
                .quantization(VoyageAiEmbedderConfig.Quantization.Enum.valueOf(toUpperCase(quantization)))
                .timeout(5000);

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

    private static String createSuccessResponse(int dimensions, IntFunction<String> valueGenerator) {
        var embedding = new StringBuilder("[");
        for (int i = 0; i < dimensions; i++) {
            if (i > 0) embedding.append(",");
            embedding.append(valueGenerator.apply(i));
        }
        embedding.append("]");
        return  Text.format("""
                {
                  "object": "list",
                  "data": [{"object": "embedding", "embedding": %s, "index": 0}],
                  "model": "voyage-3",
                  "usage": {"total_tokens": 10}
                }
                """, embedding);
    }

    private static String createSuccessResponse(int dimensions) { return createFloatSuccessResponse(dimensions); }
    private static String createFloatSuccessResponse(int dimensions) { return createSuccessResponse(dimensions, i -> Text.format("%.6f", Math.sin(i * 0.1))); }
    private static String createInt8SuccessResponse(int dimensions) { return createSuccessResponse(dimensions, i -> String.valueOf(i % 128)); }
    private static String createBinarySuccessResponse(int dimensions) { return createInt8SuccessResponse(dimensions); }
}
