// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.embedding;

import ai.vespa.embedding.config.VoyageAiEmbedderConfig;
import com.yahoo.language.process.Embedder;
import com.yahoo.language.process.InvalidInputException;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorAddress;
import com.yahoo.tensor.TensorType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for VoyageAI embedder with real API.
 *
 * These tests are disabled by default and require a real VoyageAI API key.
 * To run these tests:
 * 1. Set environment variable: export VESPA_TEST_VOYAGE_API_KEY="your-api-key"
 * 2. Run with: mvn test -Dtest=VoyageAIEmbedderIntegrationTest
 *
 * @author bjorncs
 */
@EnabledIfEnvironmentVariable(named = "VESPA_TEST_VOYAGE_API_KEY", matches = "pa-.+")
public class VoyageAIEmbedderIntegrationTest {

    @Test
    public void testRealAPIWithVoyage3() {
        var embedder = createEmbedder();

        TensorType targetType = TensorType.fromSpec("tensor<float>(d0[1024])");
        Embedder.Context context = new Embedder.Context("integration-test");

        Tensor result = embedder.embed("Hello, this is a test.", context, targetType);

        assertNotNull(result);
        assertEquals(1024, result.size());
        assertEquals(targetType, result.type());

        // Verify non-zero embeddings
        boolean hasNonZero = false;
        for (int i = 0; i < 1024; i++) {
            double val = result.get(TensorAddress.of(i));
            if (Math.abs(val) > 0.0001) {
                hasNonZero = true;
                break;
            }
        }
        assertTrue(hasNonZero, "Embedding should contain non-zero values");

        embedder.deconstruct();
    }

    @Test
    public void testRealAPIWithVoyage3Lite() {
        var configBuilder = new VoyageAiEmbedderConfig.Builder()
            .apiKeySecretRef("test_key")
            .model("voyage-3-lite")
            .dimensions(512);

        var embedder = createEmbedder(configBuilder);

        TensorType targetType = TensorType.fromSpec("tensor<float>(d0[512])");
        Embedder.Context context = new Embedder.Context("integration-test");

        Tensor result = embedder.embed("Test text for voyage-3-lite", context, targetType);

        assertNotNull(result);
        assertEquals(512, result.size());

        embedder.deconstruct();
    }

    @Test
    public void testRealAPIWithCaching() {
        var embedder = createEmbedder();

        TensorType targetType = TensorType.fromSpec("tensor<float>(d0[1024])");
        Embedder.Context context = new Embedder.Context("integration-test");

        long startTime1 = System.currentTimeMillis();
        Tensor result1 = embedder.embed("Cached text test", context, targetType);
        long duration1 = System.currentTimeMillis() - startTime1;

        long startTime2 = System.currentTimeMillis();
        Tensor result2 = embedder.embed("Cached text test", context, targetType);
        long duration2 = System.currentTimeMillis() - startTime2;

        // Second call should be much faster (cached)
        assertTrue(duration2 < duration1 / 2,
                "Cached call should be faster. First: " + duration1 + "ms, Second: " + duration2 + "ms");

        // Results should be identical
        assertEquals(result1, result2);

        embedder.deconstruct();
    }

    @Test
    public void testRealAPISemanticSimilarity() {
        var embedder = createEmbedder();

        TensorType targetType = TensorType.fromSpec("tensor<float>(d0[1024])");
        Embedder.Context context = new Embedder.Context("integration-test");

        // Similar texts
        Tensor embedding1 = embedder.embed("The cat sits on the mat", context, targetType);
        Tensor embedding2 = embedder.embed("A feline rests on the rug", context, targetType);

        // Different text
        Tensor embedding3 = embedder.embed("The quick brown fox jumps", context, targetType);

        double similarity12 = cosineSimilarity(embedding1, embedding2);
        double similarity13 = cosineSimilarity(embedding1, embedding3);

        // Similar texts should have higher similarity
        assertTrue(similarity12 > similarity13,
                "Similar texts should have higher similarity. Sim(1,2)=" + similarity12 +
                ", Sim(1,3)=" + similarity13);

        embedder.deconstruct();
    }

    @Test
    public void testRealAPIInputTypes() {
        var embedder = createEmbedder();

        TensorType targetType = TensorType.fromSpec("tensor<float>(d0[1024])");

        // Query context
        Embedder.Context queryContext = new Embedder.Context("test-query");
        Tensor queryEmbedding = embedder.embed("search query text", queryContext, targetType);

        // Document context
        Embedder.Context docContext = new Embedder.Context("test-doc");
        Tensor docEmbedding = embedder.embed("document content text", docContext, targetType);

        assertNotNull(queryEmbedding);
        assertNotNull(docEmbedding);

        // Both should have valid embeddings
        assertEquals(1024, queryEmbedding.size());
        assertEquals(1024, docEmbedding.size());

        embedder.deconstruct();
    }

    @Test
    public void testRealAPILongText() {
        var embedder = createEmbedder();

        TensorType targetType = TensorType.fromSpec("tensor<float>(d0[1024])");
        Embedder.Context context = new Embedder.Context("integration-test");

        // Very long text (will be truncated by API)
        String longText = "This is a test. ".repeat(1000);
        Tensor result = embedder.embed(longText, context, targetType);

        assertNotNull(result);
        assertEquals(1024, result.size());

        embedder.deconstruct();
    }

    @Test
    public void testRealAPIWithTruncationDisabledThrowsException() {
        var configBuilder = new VoyageAiEmbedderConfig.Builder()
                .apiKeySecretRef("test_key")
                .model("voyage-3")
                .dimensions(1024)
                .truncate(false);

        var embedder = createEmbedder(configBuilder);

        try {
            var targetType = TensorType.fromSpec("tensor<float>(d0[1024])");
            var context = new Embedder.Context("integration-test");

            // Generate very long text that exceeds token limit
            var veryLongText = "This is a test sentence. ".repeat(10000);

            var exception = assertThrows(InvalidInputException.class, () ->
                    embedder.embed(veryLongText, context, targetType)
            );
            assertEquals("VoyageAI API bad request (400): Request to model 'voyage-3' failed. " +
                    "The example at index 0 in your batch has too many tokens and does not fit into the " +
                    "model's context window of 32000 tokens. Please lower the number of tokens in the listed " +
                    "example(s) or use truncation.", exception.getMessage());
        } finally {
            embedder.deconstruct();
        }
    }

    @Test
    public void testRealAPIWithMultimodal35AllDimensions() {
        // Test all supported dimensions: 256, 512, 1024, 2048
        // Dimension is inferred from the tensor type specification
        int[] dimensions = {256, 512, 1024, 2048};

        for (int dim : dimensions) {
            VoyageAiEmbedderConfig.Builder configBuilder = new VoyageAiEmbedderConfig.Builder();
            configBuilder.apiKeySecretRef("test_key");
            configBuilder.model("voyage-multimodal-3.5");
            configBuilder.dimensions(dim);

            var embedder = createEmbedder(configBuilder);

            // Dimension is inferred from tensor type
            TensorType targetType = TensorType.fromSpec("tensor<float>(d0[" + dim + "])");
            Embedder.Context context = new Embedder.Context("integration-test");

            Tensor result = embedder.embed("Testing dimension " + dim, context, targetType);

            assertNotNull(result, "Result should not be null for dimension " + dim);
            assertEquals(dim, result.size(), "Embedding should have " + dim + " dimensions");

            embedder.deconstruct();
        }
    }

    @Test
    public void testRealAPIWithContextual3() {
        // voyage-context-3 should auto-select the contextualized embeddings endpoint
        VoyageAiEmbedderConfig.Builder configBuilder = new VoyageAiEmbedderConfig.Builder()
            .apiKeySecretRef("test_key")
            .model("voyage-context-3")
            .dimensions(1024);
        var embedder = createEmbedder(configBuilder);

        // voyage-context-3 outputs 1024 dimensions
        TensorType targetType = TensorType.fromSpec("tensor<float>(d0[1024])");
        Embedder.Context context = new Embedder.Context("integration-test");

        Tensor result = embedder.embed("Testing contextual embeddings for document retrieval", context, targetType);

        assertNotNull(result);
        assertEquals(1024, result.size());
        assertNonZeroTensor(result);

        embedder.deconstruct();
    }

    @Test
    public void testRealAPIWithInt8Quantization() {
        var configBuilder = new VoyageAiEmbedderConfig.Builder()
            .apiKeySecretRef("test_key")
            .model("voyage-4-large")
            .dimensions(1024)
            .quantization(VoyageAiEmbedderConfig.Quantization.INT8);

        var embedder = createEmbedder(configBuilder);

        TensorType targetType = TensorType.fromSpec("tensor<int8>(d0[1024])");
        Embedder.Context context = new Embedder.Context("integration-test");

        Tensor result = embedder.embed("Testing int8 quantization", context, targetType);

        assertNotNull(result);
        assertEquals(1024, result.size());
        assertEquals(targetType, result.type());
        assertNonZeroTensor(result);

        embedder.deconstruct();
    }

    @Test
    public void testRealAPIWithBinaryQuantization() {
        var configBuilder = new VoyageAiEmbedderConfig.Builder()
            .apiKeySecretRef("test_key")
            .model("voyage-4-large")
            .dimensions(1024)
            .quantization(VoyageAiEmbedderConfig.Quantization.BINARY);

        var embedder = createEmbedder(configBuilder);

        TensorType targetType = TensorType.fromSpec("tensor<int8>(d0[128])");
        Embedder.Context context = new Embedder.Context("integration-test");

        Tensor result = embedder.embed("Testing binary quantization", context, targetType);

        assertNotNull(result);
        assertEquals(128, result.size());
        assertEquals(targetType, result.type());
        assertNonZeroTensor(result);

        embedder.deconstruct();
    }

    @Test
    public void testRealAPIWithContextual3SemanticSimilarity() {
        VoyageAiEmbedderConfig.Builder configBuilder = new VoyageAiEmbedderConfig.Builder()
            .apiKeySecretRef("test_key")
            .model("voyage-context-3")
            .dimensions(1024);
        var embedder = createEmbedder(configBuilder);

        TensorType targetType = TensorType.fromSpec("tensor<float>(d0[1024])");
        Embedder.Context context = new Embedder.Context("integration-test");

        // Similar document chunks
        Tensor embedding1 = embedder.embed("Machine learning is a subset of artificial intelligence", context, targetType);
        Tensor embedding2 = embedder.embed("AI and ML are closely related fields in computer science", context, targetType);

        // Different topic
        Tensor embedding3 = embedder.embed("The recipe calls for two cups of flour and one egg", context, targetType);

        double similarity12 = cosineSimilarity(embedding1, embedding2);
        double similarity13 = cosineSimilarity(embedding1, embedding3);

        // Similar texts should have higher similarity
        assertTrue(similarity12 > similarity13,
                "Similar texts should have higher similarity. Sim(1,2)=" + similarity12 +
                ", Sim(1,3)=" + similarity13);

        embedder.deconstruct();
    }

    @Test
    public void testRealAPIWithContextual3AllDimensions() {
        // voyage-context-3 supports: 256, 512, 1024, 2048
        // Dimension is inferred from the tensor type specification
        int[] dimensions = {256, 512, 1024, 2048};

        for (int dim : dimensions) {
            VoyageAiEmbedderConfig.Builder configBuilder = new VoyageAiEmbedderConfig.Builder();
            configBuilder.apiKeySecretRef("test_key");
            configBuilder.model("voyage-context-3");
            configBuilder.dimensions(dim);

            var embedder = createEmbedder(configBuilder);

            // Dimension is inferred from tensor type
            TensorType targetType = TensorType.fromSpec("tensor<float>(d0[" + dim + "])");
            Embedder.Context context = new Embedder.Context("integration-test");

            Tensor result = embedder.embed("Testing dimension " + dim, context, targetType);

            assertNotNull(result, "Result should not be null for dimension " + dim);
            assertEquals(dim, result.size(), "Embedding should have " + dim + " dimensions");

            embedder.deconstruct();
        }
    }

    // ===== Helper Methods =====

    private VoyageAIEmbedder createEmbedder() {
        var configBuilder = new VoyageAiEmbedderConfig.Builder()
                .apiKeySecretRef("test_key")
                .dimensions(1024)
                .model("voyage-3");
        return createEmbedder(configBuilder);
    }

    private VoyageAIEmbedder createEmbedder(VoyageAiEmbedderConfig.Builder configBuilder) {
        return new VoyageAIEmbedder(
                configBuilder.build(),
                Embedder.Runtime.testInstance(),
                key -> () -> System.getenv("VESPA_TEST_VOYAGE_API_KEY"));
    }

    private double cosineSimilarity(Tensor a, Tensor b) {
        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < a.size(); i++) {
            double valA = a.get(TensorAddress.of(i));
            double valB = b.get(TensorAddress.of(i));
            dotProduct += valA * valB;
            normA += valA * valA;
            normB += valB * valB;
        }

        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private void assertNonZeroTensor(Tensor tensor) {
        for (int i = 0; i < tensor.size(); i++) {
            if (Math.abs(tensor.get(TensorAddress.of(i))) > 0.0001) return;
        }
        throw new AssertionError("Embedding should contain non-zero values");
    }
}
