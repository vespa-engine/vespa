// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.embedding;

import ai.vespa.embedding.config.HuggingFaceTeiEmbedderConfig;
import ai.vespa.secret.Secrets;
import com.yahoo.language.process.Embedder;
import com.yahoo.language.process.OverloadException;
import com.yahoo.language.process.TimeoutException;
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
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class HuggingFaceTEIEmbedderTest {

    private MockWebServer mockServer;
    private HuggingFaceTEIEmbedder embedder;
    private Embedder.Runtime runtime;

    @BeforeEach
    void setUp() throws IOException {
        mockServer = new MockWebServer();
        mockServer.start();
        runtime = Embedder.Runtime.testInstance();
    }

    @AfterEach
    void tearDown() throws IOException {
        if (embedder != null) {
            embedder.deconstruct();
        }
        mockServer.shutdown();
    }

    @Test
    void testSuccessfulEmbedding() throws Exception {
        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("[[0.1,0.2,0.3,0.4]]"));

        embedder = createEmbedder(builder -> builder.apiKeySecretRef("hf_key"));

        TensorType targetType = TensorType.fromSpec("tensor<float>(x[4])");
        Embedder.Context context = new Embedder.Context("schema.field").setEmbedderId("hf-tei");

        Tensor result = embedder.embed("hello", context, targetType);

        assertNotNull(result);
        assertEquals(targetType, result.type());

        IndexedTensor indexed = (IndexedTensor) result;
        assertEquals(0.1f, indexed.getFloat(0), 1e-6);
        assertEquals(0.4f, indexed.getFloat(3), 1e-6);

        RecordedRequest request = mockServer.takeRequest();
        assertEquals("POST", request.getMethod());
        assertEquals("/embed", request.getPath());
        assertEquals("Bearer test-hf-token", request.getHeader("Authorization"));
        String body = request.getBody().readUtf8();
        assertTrue(body.contains("\"inputs\":\"hello\""));
        assertTrue(body.contains("\"dimensions\":4"));
        assertTrue(body.contains("\"normalize\":true"));
    }

    @Test
    void testBatchEmbeddingUsesSingleRequest() throws Exception {
        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("[[1.0,2.0],[3.0,4.0]]"));

        embedder = createEmbedder();

        TensorType targetType = TensorType.fromSpec("tensor<float>(x[2])");
        Embedder.Context context = new Embedder.Context("schema.field").setEmbedderId("hf-tei");

        var tensors = embedder.embed(List.of("first", "second"), context, targetType);

        assertEquals(2, tensors.size());
        assertEquals(1, mockServer.getRequestCount());

        RecordedRequest request = mockServer.takeRequest();
        String body = request.getBody().readUtf8();
        assertTrue(body.contains("\"inputs\":[\"first\",\"second\"]"));
    }

    @Test
    void testPromptSelectionByDestinationType() throws Exception {
        mockServer.enqueue(new MockResponse().setResponseCode(200).setBody("[[0.1,0.2]]"));
        mockServer.enqueue(new MockResponse().setResponseCode(200).setBody("[[0.3,0.4]]"));

        embedder = createEmbedder(builder -> {
            builder.promptName("default_prompt");
            builder.queryPromptName("query_prompt");
            builder.documentPromptName("document_prompt");
        });

        TensorType targetType = TensorType.fromSpec("tensor<float>(x[2])");

        embedder.embed("query text", new Embedder.Context("query(q)").setEmbedderId("hf-tei"), targetType);
        embedder.embed("document text", new Embedder.Context("schema.field").setEmbedderId("hf-tei"), targetType);

        RecordedRequest queryRequest = mockServer.takeRequest();
        String queryBody = queryRequest.getBody().readUtf8();
        assertTrue(queryBody.contains("\"prompt_name\":\"query_prompt\""));

        RecordedRequest documentRequest = mockServer.takeRequest();
        String documentBody = documentRequest.getBody().readUtf8();
        assertTrue(documentBody.contains("\"prompt_name\":\"document_prompt\""));
    }

    @Test
    void testThrowsOverloadExceptionOn429() {
        mockServer.enqueue(new MockResponse().setResponseCode(429).setBody("{\"error\":\"Model is overloaded\"}"));

        embedder = createEmbedder();

        TensorType targetType = TensorType.fromSpec("tensor<float>(x[2])");
        Embedder.Context context = new Embedder.Context("schema.field").setEmbedderId("hf-tei");

        OverloadException exception = assertThrows(OverloadException.class,
                () -> embedder.embed("test", context, targetType));

        assertEquals("HuggingFace TEI API overloaded (429)", exception.getMessage());
        assertEquals(1, mockServer.getRequestCount());
    }

    @Test
    void testRejectsUnsupportedTensorType() {
        embedder = createEmbedder();

        TensorType targetType = TensorType.fromSpec("tensor<int8>(x[8])");
        Embedder.Context context = new Embedder.Context("schema.field").setEmbedderId("hf-tei");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> embedder.embed("test", context, targetType));

        assertTrue(exception.getMessage().contains("only supports tensor<float> and tensor<bfloat16>"));
        assertEquals(0, mockServer.getRequestCount());
    }

    @Test
    void testThrowsTimeoutExceptionOnSlowResponse() {
        mockServer.enqueue(new MockResponse()
                .setBodyDelay(1, TimeUnit.SECONDS)
                .setResponseCode(200)
                .setBody("[[0.1,0.2]]"));

        embedder = createEmbedder(builder -> builder.timeout(100));

        TensorType targetType = TensorType.fromSpec("tensor<float>(x[2])");
        Embedder.Context context = new Embedder.Context("schema.field").setEmbedderId("hf-tei");

        TimeoutException exception = assertThrows(TimeoutException.class,
                () -> embedder.embed("test", context, targetType));

        assertTrue(exception.getMessage().contains("timed out"));
        assertEquals(1, mockServer.getRequestCount());
    }

    private HuggingFaceTEIEmbedder createEmbedder() {
        return createEmbedder(builder -> { });
    }

    private HuggingFaceTEIEmbedder createEmbedder(Consumer<HuggingFaceTeiEmbedderConfig.Builder> customizer) {
        HuggingFaceTeiEmbedderConfig.Builder builder = new HuggingFaceTeiEmbedderConfig.Builder()
                .endpoint(mockServer.url("/embed").toString())
                .timeout(5000);
        customizer.accept(builder);
        return new HuggingFaceTEIEmbedder(builder.build(), runtime, createSecrets());
    }

    private Secrets createSecrets() {
        return key -> {
            if ("hf_key".equals(key)) {
                return () -> "test-hf-token";
            }
            return null;
        };
    }
}
