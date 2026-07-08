// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.embedding;

import ai.vespa.embedding.config.VoyageAiEmbedderConfig;
import com.yahoo.language.process.Embedder;
import com.yahoo.language.process.InvalidInputException;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.List;

import static ai.vespa.embedding.EmbedderTestUtils.assertNonZeroTensor;
import static ai.vespa.embedding.EmbedderTestUtils.cosineSimilarity;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for VoyageAI embedder with real API.
 *
 * @author bjorncs
 */
@EnabledIfEnvironmentVariable(named = "VESPA_TEST_VOYAGE_API_KEY", matches = "pa-.+")
public class VoyageAIEmbedderIntegrationTest {

    @Test
    public void testRealAPIWithVoyage3() {
        var embedder = createEmbedder("voyage-3", 1024);

        var targetType = TensorType.fromSpec("tensor<float>(d0[1024])");
        var context = new Embedder.Context("integration-test");

        Tensor result = embedder.embed("Hello, this is a test.", context, targetType);

        assertNotNull(result);
        assertEquals(1024, result.size());
        assertNonZeroTensor(result);

        embedder.deconstruct();
    }

    @Test
    public void testRealAPIWithVoyage3Lite() {
        var embedder = createEmbedder("voyage-3-lite", 512);

        var targetType = TensorType.fromSpec("tensor<float>(d0[512])");
        var context = new Embedder.Context("integration-test");

        Tensor result = embedder.embed("Test text for voyage-3-lite", context, targetType);

        assertNotNull(result);
        assertEquals(512, result.size());

        embedder.deconstruct();
    }

    @Test
    public void testRealAPISemanticSimilarity() {
        var embedder = createEmbedder("voyage-3", 1024);

        var targetType = TensorType.fromSpec("tensor<float>(d0[1024])");
        var context = new Embedder.Context("integration-test");

        Tensor embedding1 = embedder.embed("The cat sits on the mat", context, targetType);
        Tensor embedding2 = embedder.embed("A feline rests on the rug", context, targetType);
        Tensor embedding3 = embedder.embed("The quick brown fox jumps", context, targetType);

        double similarity12 = cosineSimilarity(embedding1, embedding2);
        double similarity13 = cosineSimilarity(embedding1, embedding3);

        assertTrue(similarity12 > similarity13,
                "Similar texts should have higher similarity. Sim(1,2)=%f, Sim(1,3)=%f"
                        .formatted(similarity12, similarity13));

        embedder.deconstruct();
    }

    @Test
    public void testRealAPIInputTypes() {
        var embedder = createEmbedder("voyage-3", 1024);

        var targetType = TensorType.fromSpec("tensor<float>(d0[1024])");

        Tensor queryEmbedding = embedder.embed("search query text", new Embedder.Context("test-query"), targetType);
        Tensor docEmbedding = embedder.embed("document content text", new Embedder.Context("test-doc"), targetType);

        assertNotNull(queryEmbedding);
        assertNotNull(docEmbedding);
        assertEquals(1024, queryEmbedding.size());
        assertEquals(1024, docEmbedding.size());

        embedder.deconstruct();
    }

    @Test
    public void testRealAPILongText() {
        var embedder = createEmbedder("voyage-3", 1024);

        var targetType = TensorType.fromSpec("tensor<float>(d0[1024])");
        var context = new Embedder.Context("integration-test");

        String longText = "This is a test. ".repeat(1000);
        Tensor result = embedder.embed(longText, context, targetType);

        assertNotNull(result);
        assertEquals(1024, result.size());

        embedder.deconstruct();
    }

    @Test
    public void testRealAPIWithTruncationDisabledThrowsException() {
        var embedder = createEmbedderWithConfig(b -> b.truncate(false).model("voyage-3").dimensions(1024));

        try {
            var targetType = TensorType.fromSpec("tensor<float>(d0[1024])");
            var context = new Embedder.Context("integration-test");

            var veryLongText = "This is a test sentence. ".repeat(10000);

            var exception = assertThrows(InvalidInputException.class, () ->
                    embedder.embed(veryLongText, context, targetType));
            assertEquals("Embedding API bad request (400): Request to model 'voyage-3' failed. " +
                    "The example at index 0 in your batch has too many tokens and does not fit into the " +
                    "model's context window of 32000 tokens. Please lower the number of tokens in the listed " +
                    "example(s) or use truncation.", exception.getMessage());
        } finally {
            embedder.deconstruct();
        }
    }

    @Test
    public void testRealAPIWithMultimodal35AllDimensions() {
        int[] dimensions = {256, 512, 1024, 2048};

        for (int dim : dimensions) {
            var embedder = createEmbedder("voyage-multimodal-3.5", dim);

            var targetType = TensorType.fromSpec("tensor<float>(d0[%d])".formatted(dim));
            var context = new Embedder.Context("integration-test");

            Tensor result = embedder.embed("Testing dimension " + dim, context, targetType);

            assertNotNull(result, "Result should not be null for dimension " + dim);
            assertEquals(dim, result.size(), "Embedding should have " + dim + " dimensions");

            embedder.deconstruct();
        }
    }

    @Test
    public void testRealAPIWithContextual3() {
        var embedder = createEmbedder("voyage-context-3", 1024);

        var targetType = TensorType.fromSpec("tensor<float>(d0[1024])");
        var context = new Embedder.Context("integration-test");

        Tensor result = embedder.embed("Testing contextual embeddings for document retrieval", context, targetType);

        assertNotNull(result);
        assertEquals(1024, result.size());
        assertNonZeroTensor(result);

        embedder.deconstruct();
    }

    @Test
    public void testRealAPIWithInt8Quantization() {
        var embedder = createEmbedder("voyage-4-large", 1024, VoyageAiEmbedderConfig.Quantization.Enum.INT8);

        var targetType = TensorType.fromSpec("tensor<int8>(d0[1024])");
        var context = new Embedder.Context("integration-test");

        Tensor result = embedder.embed("Testing int8 quantization", context, targetType);

        assertNotNull(result);
        assertEquals(1024, result.size());
        assertNonZeroTensor(result);

        embedder.deconstruct();
    }

    @Test
    public void testRealAPIWithBinaryQuantization() {
        var embedder = createEmbedder("voyage-4-large", 1024, VoyageAiEmbedderConfig.Quantization.Enum.BINARY);

        var targetType = TensorType.fromSpec("tensor<int8>(d0[128])");
        var context = new Embedder.Context("integration-test");

        Tensor result = embedder.embed("Testing binary quantization", context, targetType);

        assertNotNull(result);
        assertEquals(128, result.size());
        assertNonZeroTensor(result);

        embedder.deconstruct();
    }

    @Test
    public void testRealAPIWithContextual3SemanticSimilarity() {
        var embedder = createEmbedder("voyage-context-3", 1024);

        var targetType = TensorType.fromSpec("tensor<float>(d0[1024])");
        var context = new Embedder.Context("integration-test");

        Tensor embedding1 = embedder.embed("Machine learning is a subset of artificial intelligence", context, targetType);
        Tensor embedding2 = embedder.embed("AI and ML are closely related fields in computer science", context, targetType);
        Tensor embedding3 = embedder.embed("The recipe calls for two cups of flour and one egg", context, targetType);

        double similarity12 = cosineSimilarity(embedding1, embedding2);
        double similarity13 = cosineSimilarity(embedding1, embedding3);

        assertTrue(similarity12 > similarity13,
                "Similar texts should have higher similarity. Sim(1,2)=%f, Sim(1,3)=%f"
                        .formatted(similarity12, similarity13));

        embedder.deconstruct();
    }

    @Test
    public void testRealAPIWithContextual3AllDimensions() {
        int[] dimensions = {256, 512, 1024, 2048};

        for (int dim : dimensions) {
            var embedder = createEmbedder("voyage-context-3", dim);

            var targetType = TensorType.fromSpec("tensor<float>(d0[%d])".formatted(dim));
            var context = new Embedder.Context("integration-test");

            Tensor result = embedder.embed("Testing dimension " + dim, context, targetType);

            assertNotNull(result, "Result should not be null for dimension " + dim);
            assertEquals(dim, result.size());

            embedder.deconstruct();
        }
    }

    @Test
    public void testRealAPIWithContextual3BatchEmbedding() {
        var embedder = createEmbedder("voyage-context-3", 1024);

        var targetType = TensorType.fromSpec("tensor<float>(d0[1024])");
        var context = new Embedder.Context("integration-test");

        var chunks = List.of(
                "Machine learning is a branch of artificial intelligence.",
                "It enables computers to learn from data.",
                "Deep learning is a subset of machine learning.");

        var results = embedder.embed(chunks, context, targetType);

        assertEquals(3, results.size());
        for (var result : results) {
            assertNotNull(result);
            assertEquals(1024, result.size());
            assertNonZeroTensor(result);
        }

        double sim01 = cosineSimilarity(results.get(0), results.get(1));
        double sim02 = cosineSimilarity(results.get(0), results.get(2));
        assertTrue(sim01 > 0.5, "Related chunks should have reasonable similarity. Sim(0,1)=%f".formatted(sim01));
        assertTrue(sim02 > 0.5, "Related chunks should have reasonable similarity. Sim(0,2)=%f".formatted(sim02));

        embedder.deconstruct();
    }

    @Test
    public void testRealAPIWithContextual3BatchAllDimensions() {
        int[] dimensions = {256, 512, 1024, 2048};

        for (int dim : dimensions) {
            var embedder = createEmbedder("voyage-context-3", dim);

            var targetType = TensorType.fromSpec("tensor<float>(d0[%d])".formatted(dim));
            var context = new Embedder.Context("integration-test");

            var chunks = List.of("First chunk", "Second chunk");
            var results = embedder.embed(chunks, context, targetType);

            assertEquals(2, results.size());
            for (var result : results) {
                assertNotNull(result);
                assertEquals(dim, result.size());
            }

            embedder.deconstruct();
        }
    }

    @Test
    public void testRealAPIWithVoyage3BatchEmbedding() {
        var embedder = createEmbedder("voyage-3", 1024);

        var targetType = TensorType.fromSpec("tensor<float>(d0[1024])");
        var context = new Embedder.Context("integration-test");

        var texts = List.of(
                "Machine learning is a branch of artificial intelligence.",
                "The weather today is sunny and warm.",
                "Deep learning uses neural networks.");

        var results = embedder.embed(texts, context, targetType);

        assertEquals(3, results.size());
        for (var result : results) {
            assertNotNull(result);
            assertEquals(1024, result.size());
            assertNonZeroTensor(result);
        }

        double sim01 = cosineSimilarity(results.get(0), results.get(1));
        double sim02 = cosineSimilarity(results.get(0), results.get(2));
        assertTrue(sim02 > sim01, "ML texts should be more similar. Sim(0,2)=%f, Sim(0,1)=%f".formatted(sim02, sim01));

        embedder.deconstruct();
    }

    // ===== Helper Methods =====

    private static VoyageAIEmbedder createEmbedder(String model, int dimensions) {
        return createEmbedder(model, dimensions, VoyageAiEmbedderConfig.Quantization.Enum.AUTO);
    }

    private static VoyageAIEmbedder createEmbedder(String model, int dimensions,
                                                    VoyageAiEmbedderConfig.Quantization.Enum quantization) {
        return createEmbedderWithConfig(b -> b.model(model).dimensions(dimensions).quantization(quantization));
    }

    private static VoyageAIEmbedder createEmbedderWithConfig(
            java.util.function.Consumer<VoyageAiEmbedderConfig.Builder> customize) {
        var builder = new VoyageAiEmbedderConfig.Builder()
                .apiKeySecretRef("test_key")
                .endpoint("https://api.voyageai.com/v1/embeddings");
        customize.accept(builder);
        return new VoyageAIEmbedder(builder.build(), Embedder.Runtime.testInstance(),
                key -> () -> System.getenv("VESPA_TEST_VOYAGE_API_KEY"));
    }
}
