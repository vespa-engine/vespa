// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.embedding;

import ai.vespa.embedding.config.MistralEmbedderConfig;
import com.yahoo.language.process.Embedder;
import com.yahoo.tensor.TensorType;
import com.yahoo.text.Text;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static ai.vespa.embedding.EmbedderTestUtils.createMockSecrets;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for Mistral embedder using MockWebServer to simulate API responses.
 *
 * @author bjorncs
 */
public class MistralEmbedderTest {

    private MockWebServer mockServer;
    private MistralEmbedder embedder;
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
    public void testMistralEmbedRequest() throws Exception {
        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(createJsonArrayResponse(1024)));

        embedder = createEmbedder("mistral-embed", 1024, MistralEmbedderConfig.Quantization.Enum.INT8);

        var targetType = TensorType.fromSpec("tensor<int8>(d0[1024])");
        var context = new Embedder.Context("test-embedder");
        var result = embedder.embed("test", context, targetType);

        assertNotNull(result);
        assertEquals(1024, result.size());

        var request = mockServer.takeRequest();
        var body = request.getBody().readUtf8();
        assertTrue(body.contains("\"output_dtype\":\"int8\""));
        assertFalse(body.contains("\"encoding_format\""));
        assertFalse(body.contains("\"dimensions\""));
        assertFalse(body.contains("\"output_dimension\""), "mistral-embed does not support output_dimension");
    }

    @Test
    public void testCodestralEmbedRequest() throws Exception {
        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(createJsonArrayResponse(2048)));

        embedder = createEmbedder("codestral-embed", 2048, MistralEmbedderConfig.Quantization.Enum.AUTO);

        var targetType = TensorType.fromSpec("tensor<float>(d0[2048])");
        var context = new Embedder.Context("test-embedder");
        var result = embedder.embed("test", context, targetType);

        assertNotNull(result);
        assertEquals(2048, result.size());

        var request = mockServer.takeRequest();
        var body = request.getBody().readUtf8();
        assertTrue(body.contains("\"output_dimension\":2048"), "codestral-embed supports output_dimension");
        assertTrue(body.contains("\"output_dtype\":\"float\""));
    }

    @Test
    public void testBatchEmbedding() throws Exception {
        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(createJsonArrayBatchResponse(3, 1024)));

        embedder = createEmbedder("mistral-embed", 1024, MistralEmbedderConfig.Quantization.Enum.INT8);

        var targetType = TensorType.fromSpec("tensor<int8>(d0[1024])");
        var context = new Embedder.Context("test-embedder");
        var texts = List.of("Hello", "World", "Test");
        var results = embedder.embed(texts, context, targetType);

        assertEquals(3, results.size());
        for (var result : results) {
            assertEquals(1024, result.size());
            assertEquals(targetType, result.type());
        }
        assertEquals(1, mockServer.getRequestCount());

        var request = mockServer.takeRequest();
        var body = request.getBody().readUtf8();
        assertTrue(body.contains("\"input\":[\"Hello\",\"World\",\"Test\"]"));
    }

    // ===== Helpers =====

    private MistralEmbedder createEmbedder(String model, int dimensions, MistralEmbedderConfig.Quantization.Enum quantization) {
        return new MistralEmbedder(mistralConfigBuilder(model, dimensions, quantization).build(), runtime, createMockSecrets());
    }

    private MistralEmbedderConfig.Builder mistralConfigBuilder(String model, int dimensions,
                                                                MistralEmbedderConfig.Quantization.Enum quantization) {
        return new MistralEmbedderConfig.Builder()
                .apiKeySecretRef("test_key")
                .endpoint(mockServer.url("/v1/embeddings").toString())
                .model(model)
                .dimensions(dimensions)
                .quantization(quantization);
    }

    private static String createJsonArrayResponse(int dimensions) {
        var values = new StringBuilder();
        for (int i = 0; i < dimensions; i++) {
            if (i > 0) values.append(",");
            values.append((i % 256) - 128);
        }
        return Text.format("""
                {"object":"list","data":[{"object":"embedding","embedding":[%s],"index":0}],"model":"mistral-embed","usage":{"prompt_tokens":10,"total_tokens":10}}
                """, values);
    }

    private static String createJsonArrayBatchResponse(int batchSize, int dimensions) {
        var data = new StringBuilder();
        for (int b = 0; b < batchSize; b++) {
            var values = new StringBuilder();
            for (int i = 0; i < dimensions; i++) {
                if (i > 0) values.append(",");
                values.append((i % 256) - 128);
            }
            if (b > 0) data.append(",");
            data.append(Text.format("{\"object\":\"embedding\",\"embedding\":[%s],\"index\":%d}", values, b));
        }
        return Text.format("""
                {"object":"list","data":[%s],"model":"mistral-embed","usage":{"prompt_tokens":30,"total_tokens":30}}
                """, data);
    }
}
