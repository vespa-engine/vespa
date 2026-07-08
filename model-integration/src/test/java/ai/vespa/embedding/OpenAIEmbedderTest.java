// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.embedding;

import ai.vespa.embedding.config.OpenaiEmbedderConfig;
import com.yahoo.language.process.Embedder;
import com.yahoo.tensor.TensorType;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

import static ai.vespa.embedding.EmbedderTestUtils.createBase64EmbeddingResponse;
import static ai.vespa.embedding.EmbedderTestUtils.createMockSecrets;
import static ai.vespa.embedding.EmbedderTestUtils.encodeFloatsToBase64;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for OpenAI embedder request shape and response parsing. Shared HTTP transport behavior
 * (status-code handling, timeouts, retries) is covered by {@link AbstractHttpEmbedderTest}.
 *
 * @author bjorncs
 */
public class OpenAIEmbedderTest {

    private MockWebServer mockServer;
    private OpenAIEmbedder embedder;
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
        var result = embedder.embed("Hello, world!", context, targetType);

        assertNotNull(result);
        assertEquals(1024, result.size());
        assertEquals(targetType, result.type());

        RecordedRequest request = mockServer.takeRequest();
        assertEquals("POST", request.getMethod());
        assertEquals("/v1/embeddings", request.getPath());
        assertTrue(request.getHeader("Authorization").startsWith("Bearer "));
        var body = request.getBody().readUtf8();
        assertTrue(body.contains("\"model\":\"text-embedding-3-small\""));
        assertTrue(body.contains("\"encoding_format\":\"base64\""));
        assertTrue(body.contains("\"dimensions\":1024"));
    }

    @Test
    public void testBatchEmbedding() throws Exception {
        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(createBatchSuccessResponse(3, 1024)));

        embedder = createEmbedder();
        var targetType = TensorType.fromSpec("tensor<float>(d0[1024])");
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

    @Test
    public void testPrependPrefixes() throws Exception {
        var targetType = TensorType.fromSpec("tensor<float>(d0[1024])");
        var texts = List.of("Hello", "World");

        // Both prependQuery and prependDocument configured
        var config = openaiConfigBuilder(1024)
                .prependQuery("query: ")
                .prependDocument("document: ");
        embedder = new OpenAIEmbedder(config.build(), runtime, createMockSecrets());

        // QUERY context — query prefix applied
        mockServer.enqueue(new MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(createBatchSuccessResponse(2, 1024)));
        var queryContext = new Embedder.Context("query(embedding)");
        embedder.embed(texts, queryContext, targetType);
        var queryRequest = mockServer.takeRequest();
        var queryBody = queryRequest.getBody().readUtf8();
        assertTrue(queryBody.contains("\"input\":[\"query: Hello\",\"query: World\"]"),
                "Expected query prefix in request body, got: " + queryBody);

        // DOCUMENT context — document prefix applied
        mockServer.enqueue(new MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(createBatchSuccessResponse(2, 1024)));
        var docContext = new Embedder.Context("test-embedder");
        embedder.embed(texts, docContext, targetType);
        var docRequest = mockServer.takeRequest();
        var docBody = docRequest.getBody().readUtf8();
        assertTrue(docBody.contains("\"input\":[\"document: Hello\",\"document: World\"]"),
                "Expected document prefix in request body, got: " + docBody);

        embedder.deconstruct();

        // Only prependDocument configured — QUERY inputs must remain unmodified
        var docOnlyConfig = openaiConfigBuilder(1024).prependDocument("document: ");
        embedder = new OpenAIEmbedder(docOnlyConfig.build(), runtime, createMockSecrets());
        mockServer.enqueue(new MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(createBatchSuccessResponse(2, 1024)));
        embedder.embed(texts, queryContext, targetType);
        var docOnlyQueryRequest = mockServer.takeRequest();
        var docOnlyQueryBody = docOnlyQueryRequest.getBody().readUtf8();
        assertTrue(docOnlyQueryBody.contains("\"input\":[\"Hello\",\"World\"]"),
                "Expected no prefix for QUERY when only prependDocument is set, got: " + docOnlyQueryBody);
    }

    @Test
    public void testBatchingConfigEnabled() {
        var config = openaiConfigBuilder(1024);
        config.batching.maxSize(16);
        config.batching.maxDelayMillis(200);
        embedder = new OpenAIEmbedder(config.build(), runtime, createMockSecrets());

        var batching = embedder.batchingConfig();
        assertTrue(batching.isEnabled());
        assertEquals(16, batching.maxSize());
        assertEquals(Duration.ofMillis(200), batching.maxDelay());
    }

    // ===== Helper Methods =====

    private OpenAIEmbedder createEmbedder() { return createEmbedder(1024); }

    private OpenAIEmbedder createEmbedder(int dimensions) {
        return new OpenAIEmbedder(openaiConfigBuilder(dimensions).build(), runtime, createMockSecrets());
    }

    private OpenaiEmbedderConfig.Builder openaiConfigBuilder(int dimensions) {
        return new OpenaiEmbedderConfig.Builder()
                .apiKeySecretRef("test_key")
                .endpoint(mockServer.url("/v1/embeddings").toString())
                .model("text-embedding-3-small")
                .dimensions(dimensions);
    }

    private static String createSuccessResponse(int dimensions) {
        return createBase64EmbeddingResponse(encodeFloatsToBase64(dimensions, 0));
    }

    private static String createBatchSuccessResponse(int batchSize, int dimensions) {
        var embeddings = new String[batchSize];
        for (int b = 0; b < batchSize; b++) embeddings[b] = encodeFloatsToBase64(dimensions, b * 1000);
        return createBase64EmbeddingResponse(embeddings);
    }
}
