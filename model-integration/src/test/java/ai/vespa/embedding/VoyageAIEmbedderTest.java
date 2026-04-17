// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.embedding;

import ai.vespa.embedding.config.VoyageAiEmbedderConfig;
import com.yahoo.language.process.Embedder;
import com.yahoo.tensor.IndexedTensor;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Duration;
import java.util.Base64;
import java.util.List;

import static ai.vespa.embedding.EmbedderTestUtils.createBase64EmbeddingResponse;
import static ai.vespa.embedding.EmbedderTestUtils.createMockSecrets;
import static ai.vespa.embedding.EmbedderTestUtils.encodeBytesToBase64;
import static ai.vespa.embedding.EmbedderTestUtils.encodeFloatsToBase64;
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
        if (embedder != null) embedder.deconstruct();
        mockServer.shutdown();
    }

    @Test
    public void testSuccessfulEmbedding() throws Exception {
        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(createSuccessResponse(1024)));

        embedder = createEmbedder();

        var targetType = TensorType.fromSpec("tensor<float>(d0[1024])");
        var context = new Embedder.Context("test-embedder");
        Tensor result = embedder.embed("Hello, world!", context, targetType);

        assertNotNull(result);
        assertEquals(1024, result.size());
        assertEquals(targetType, result.type());

        RecordedRequest request = mockServer.takeRequest();
        assertEquals("POST", request.getMethod());
        assertEquals("/v1/embeddings", request.getPath());
        assertTrue(request.getHeader("Authorization").startsWith("Bearer "));
        var body = request.getBody().readUtf8();
        assertTrue(body.contains("\"model\":\"voyage-3\""));
        assertTrue(body.contains("\"encoding_format\":\"base64\""));
    }

    @Test
    public void testInvalidTensorType() {
        embedder = createEmbedder();
        var invalidType = TensorType.fromSpec("tensor<float>(d0[32],d1[32])");
        var context = new Embedder.Context("test-embedder");

        assertThrows(IllegalArgumentException.class, () -> embedder.embed("test", context, invalidType));
    }

    @Test
    public void testInputTypeInRequest() throws Exception {
        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody(createSuccessResponse(1024)));

        embedder = createEmbedder();
        var targetType = TensorType.fromSpec("tensor<float>(d0[1024])");
        var context = new Embedder.Context("test-embedder");

        embedder.embed("test", context, targetType);

        RecordedRequest request = mockServer.takeRequest();
        var body = request.getBody().readUtf8();
        assertTrue(body.contains("\"input_type\":\"document\""));
    }

    @Test
    public void testAutoQuantizationWithFloatTensor() throws Exception {
        embedder = createEmbedder(1024, "AUTO");

        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(createFloatSuccessResponse(1024)));

        var targetType = TensorType.fromSpec("tensor<float>(x[1024])");
        var context = new Embedder.Context("test-embedder");

        Tensor result = embedder.embed("test", context, targetType);

        assertNotNull(result);
        assertEquals(1024, result.size());
        assertEquals(TensorType.Value.FLOAT, result.type().valueType());

        RecordedRequest request = mockServer.takeRequest();
        var body = request.getBody().readUtf8();
        assertTrue(body.contains("\"output_dtype\":\"float\""));
        assertTrue(body.contains("\"output_dimension\":1024"));
        assertTrue(body.contains("\"encoding_format\":\"base64\""));
    }

    @Test
    public void testAutoQuantizationWithInt8Tensor() throws Exception {
        embedder = createEmbedder(1024, "AUTO");

        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(createInt8SuccessResponse(1024)));

        var targetType = TensorType.fromSpec("tensor<int8>(x[1024])");
        var context = new Embedder.Context("test-embedder");

        Tensor result = embedder.embed("test", context, targetType);

        assertNotNull(result);
        assertEquals(1024, result.size());
        assertEquals(TensorType.Value.INT8, result.type().valueType());

        RecordedRequest request = mockServer.takeRequest();
        var body = request.getBody().readUtf8();
        assertTrue(body.contains("\"output_dtype\":\"int8\""));
        assertTrue(body.contains("\"output_dimension\":1024"));
    }

    @Test
    public void testAutoQuantizationWithBinaryTensor() throws Exception {
        embedder = createEmbedder(1024, "AUTO");

        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(createBinarySuccessResponse(128)));

        var targetType = TensorType.fromSpec("tensor<int8>(x[128])");
        var context = new Embedder.Context("test-embedder");

        Tensor result = embedder.embed("test", context, targetType);

        assertNotNull(result);
        assertEquals(128, result.size());
        assertEquals(TensorType.Value.INT8, result.type().valueType());

        RecordedRequest request = mockServer.takeRequest();
        var body = request.getBody().readUtf8();
        assertTrue(body.contains("\"output_dtype\":\"binary\""));
        assertTrue(body.contains("\"output_dimension\":1024"));
    }

    @Test
    public void testExplicitFloatQuantizationValidation() {
        embedder = createEmbedder(1024, "FLOAT");

        var int8Type = TensorType.fromSpec("tensor<int8>(x[1024])");
        var context = new Embedder.Context("test-embedder");

        var exception = assertThrows(IllegalArgumentException.class,
                () -> embedder.embed("test", context, int8Type));
        assertEquals("Quantization 'float' is incompatible with tensor type tensor<int8>(x[1024]).", exception.getMessage());
    }

    @Test
    public void testExplicitInt8QuantizationValidation() {
        embedder = createEmbedder(1024, "INT8");

        var floatType = TensorType.fromSpec("tensor<float>(x[1024])");
        var context = new Embedder.Context("test-embedder");

        var exception = assertThrows(IllegalArgumentException.class,
                () -> embedder.embed("test", context, floatType));
        assertEquals("Quantization 'int8' is incompatible with tensor type tensor<float>(x[1024]).", exception.getMessage());
    }

    @Test
    public void testExplicitBinaryQuantizationValidation() {
        embedder = createEmbedder(1024, "BINARY");

        var targetType = TensorType.fromSpec("tensor<int8>(x[1024])");
        var context = new Embedder.Context("test-embedder");

        var exception = assertThrows(IllegalArgumentException.class,
                () -> embedder.embed("test", context, targetType));
        assertEquals("Tensor dimension 1024 does not match required dimension 128.", exception.getMessage());
    }

    @Test
    public void testDimensionMismatchBetweenConfigAndTensorType() {
        embedder = createEmbedder(512, "AUTO");

        var targetType = TensorType.fromSpec("tensor<float>(d0[1024])");
        var context = new Embedder.Context("test-embedder");

        var exception = assertThrows(IllegalArgumentException.class, () -> embedder.embed("test", context, targetType));
        assertEquals("Tensor dimension 1024 does not match configured dimension 512.", exception.getMessage());
    }

    @Test
    public void testAutoQuantizationWithBfloat16Tensor() throws Exception {
        embedder = createEmbedder(1024, "AUTO");

        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(createFloatSuccessResponse(1024)));

        var targetType = TensorType.fromSpec("tensor<bfloat16>(x[1024])");
        var context = new Embedder.Context("test-embedder");

        Tensor result = embedder.embed("test", context, targetType);

        assertNotNull(result);
        assertEquals(1024, result.size());
        assertEquals(TensorType.Value.BFLOAT16, result.type().valueType());

        RecordedRequest request = mockServer.takeRequest();
        var body = request.getBody().readUtf8();
        assertTrue(body.contains("\"output_dtype\":\"float\""));
        assertTrue(body.contains("\"output_dimension\":1024"));
    }

    @Test
    public void testExplicitFloatQuantizationWithBfloat16Tensor() throws Exception {
        embedder = createEmbedder(1024, "FLOAT");

        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(createFloatSuccessResponse(1024)));

        var targetType = TensorType.fromSpec("tensor<bfloat16>(x[1024])");
        var context = new Embedder.Context("test-embedder");

        Tensor result = embedder.embed("test", context, targetType);

        assertNotNull(result);
        assertEquals(1024, result.size());
        assertEquals(TensorType.Value.BFLOAT16, result.type().valueType());

        RecordedRequest request = mockServer.takeRequest();
        var body = request.getBody().readUtf8();
        assertTrue(body.contains("\"output_dtype\":\"float\""));
    }

    @Test
    public void testBatchEmbeddingNonContextualModel() throws Exception {
        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(createFloatBatchSuccessResponse(3, 1024)));

        embedder = createEmbedder();

        var targetType = TensorType.fromSpec("tensor<float>(d0[1024])");
        var context = new Embedder.Context("test-embedder");
        var texts = List.of("Hello, world!", "Second text", "Third text");
        var results = embedder.embed(texts, context, targetType);

        assertNotNull(results);
        assertEquals(3, results.size());
        for (var result : results) {
            assertEquals(1024, result.size());
            assertEquals(targetType, result.type());
        }

        assertEquals(1, mockServer.getRequestCount());

        RecordedRequest request = mockServer.takeRequest();
        var body = request.getBody().readUtf8();
        assertTrue(body.contains("\"input\":[\"Hello, world!\",\"Second text\",\"Third text\"]"));
        assertTrue(body.contains("\"model\":\"voyage-3\""));
    }

    @Test
    public void testBatchingConfigDisabledByDefault() {
        embedder = createEmbedder();
        assertEquals(Embedder.Batching.DISABLED, embedder.batchingConfig());
    }

    @Test
    public void testBatchingConfigEnabled() {
        var configBuilder = voyageConfigBuilder(1024);
        configBuilder.batching.maxSize(16);
        configBuilder.batching.maxDelayMillis(200);
        embedder = new VoyageAIEmbedder(configBuilder.build(), runtime, createMockSecrets());

        var batching = embedder.batchingConfig();
        assertTrue(batching.isEnabled());
        assertEquals(16, batching.maxSize());
        assertEquals(Duration.ofMillis(200), batching.maxDelay());
    }

    @Test
    public void testBatchingConfigDisabledForContextualModel() {
        var configBuilder = voyageConfigBuilder(1024).model("voyage-context-3");
        configBuilder.batching.maxSize(16);
        embedder = new VoyageAIEmbedder(configBuilder.build(), runtime, createMockSecrets());

        assertEquals(Embedder.Batching.DISABLED, embedder.batchingConfig());
    }

    @Test
    public void testContextualModelRejectsMultiTextBatch() {
        var config = voyageConfigBuilder(1024).model("voyage-context-3").build();
        embedder = new VoyageAIEmbedder(config, runtime, createMockSecrets());

        var targetType = TensorType.fromSpec("tensor<float>(d0[1024])");
        var context = new Embedder.Context("test-embedder");
        var texts = List.of("doc1", "doc2");

        var exception = assertThrows(IllegalArgumentException.class,
                () -> embedder.embed(texts, context, targetType));
        assertTrue(exception.getMessage().contains("Contextual models do not support batching"));
    }

    @Test
    public void testBase64FloatRoundTrip() {
        float[] expected = {1.0f, -0.5f, 0.0f, Float.MAX_VALUE};
        var buffer = ByteBuffer.allocate(expected.length * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (float v : expected) buffer.putFloat(v);
        var base64 = Base64.getEncoder().encodeToString(buffer.array());

        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(createBase64EmbeddingResponse(base64)));

        embedder = createEmbedder(4, "FLOAT");
        var targetType = TensorType.fromSpec("tensor<float>(x[4])");
        var context = new Embedder.Context("test-embedder");
        var result = (IndexedTensor) embedder.embed("test", context, targetType);

        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], result.getFloat(i), 0.0f, "Mismatch at index " + i);
        }
    }

    @Test
    public void testBase64Int8RoundTrip() {
        byte[] expected = {0, 1, -1, 127, -128, 42};
        var base64 = Base64.getEncoder().encodeToString(expected);

        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(createBase64EmbeddingResponse(base64)));

        embedder = createEmbedder(6, "INT8");
        var targetType = TensorType.fromSpec("tensor<int8>(x[6])");
        var context = new Embedder.Context("test-embedder");
        var result = (IndexedTensor) embedder.embed("test", context, targetType);

        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], (byte) result.getFloat(i), "Mismatch at index " + i);
        }
    }

    // ===== Helper Methods =====

    private VoyageAIEmbedder createEmbedder() { return createEmbedder(1024, "AUTO"); }

    private VoyageAIEmbedder createEmbedder(int dimensions, String quantization) {
        var config = voyageConfigBuilder(dimensions)
                .quantization(VoyageAiEmbedderConfig.Quantization.Enum.valueOf(quantization))
                .build();
        return new VoyageAIEmbedder(config, runtime, createMockSecrets());
    }

    private VoyageAiEmbedderConfig.Builder voyageConfigBuilder(int dimensions) {
        return new VoyageAiEmbedderConfig.Builder()
                .apiKeySecretRef("test_key")
                .endpoint(mockServer.url("/v1/embeddings").toString())
                .model("voyage-3")
                .dimensions(dimensions);
    }

    private static String createSuccessResponse(int dimensions) { return createFloatSuccessResponse(dimensions); }

    private static String createFloatSuccessResponse(int dimensions) {
        return createBase64EmbeddingResponse(encodeFloatsToBase64(dimensions, 0));
    }

    private static String createFloatBatchSuccessResponse(int batchSize, int dimensions) {
        var embeddings = new String[batchSize];
        for (int b = 0; b < batchSize; b++) embeddings[b] = encodeFloatsToBase64(dimensions, b * 1000);
        return createBase64EmbeddingResponse(embeddings);
    }

    private static String createInt8SuccessResponse(int dimensions) {
        return createBase64EmbeddingResponse(encodeBytesToBase64(dimensions));
    }

    private static String createBinarySuccessResponse(int dimensions) { return createInt8SuccessResponse(dimensions); }
}
