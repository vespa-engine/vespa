// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.embedding;

import ai.vespa.secret.Secret;
import ai.vespa.secret.Secrets;
import com.yahoo.embedding.voyageai.VoyageAiEmbedderConfig;
import com.yahoo.language.process.Embedder;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorAddress;
import com.yahoo.tensor.TensorType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for VoyageAI embedder with real API.
 *
 * These tests are disabled by default and require a real VoyageAI API key.
 * To run these tests:
 * 1. Set environment variable: export VOYAGE_API_KEY="your-api-key"
 * 2. Run with: mvn test -Dtest=VoyageAIEmbedderIntegrationTest
 *
 * NOTE: These tests make real API calls and may incur costs.
 */
@EnabledIfEnvironmentVariable(named = "VOYAGE_API_KEY", matches = ".+")
public class VoyageAIEmbedderIntegrationTest {

    @Test
    public void testRealAPIWithVoyage3() {
        String apiKey = System.getenv("VOYAGE_API_KEY");

        VoyageAIEmbedder embedder = createEmbedder(apiKey, "voyage-3");

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
        String apiKey = System.getenv("VOYAGE_API_KEY");

        VoyageAIEmbedder embedder = createEmbedder(apiKey, "voyage-3-lite");

        TensorType targetType = TensorType.fromSpec("tensor<float>(d0[512])");
        Embedder.Context context = new Embedder.Context("integration-test");

        Tensor result = embedder.embed("Test text for voyage-3-lite", context, targetType);

        assertNotNull(result);
        assertEquals(512, result.size());

        embedder.deconstruct();
    }

    @Test
    public void testRealAPIWithCaching() {
        String apiKey = System.getenv("VOYAGE_API_KEY");

        VoyageAIEmbedder embedder = createEmbedder(apiKey, "voyage-3");

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
    public void testRealAPIWithNormalization() {
        String apiKey = System.getenv("VOYAGE_API_KEY");

        VoyageAiEmbedderConfig.Builder configBuilder = new VoyageAiEmbedderConfig.Builder();
        configBuilder.apiKeySecretName("test_key");
        configBuilder.model("voyage-3");
        configBuilder.normalize(true);

        VoyageAIEmbedder embedder = new VoyageAIEmbedder(
                configBuilder.build(),
                Embedder.Runtime.testInstance(),
                createSecrets(apiKey)
        );

        TensorType targetType = TensorType.fromSpec("tensor<float>(d0[1024])");
        Embedder.Context context = new Embedder.Context("integration-test");

        Tensor result = embedder.embed("Normalization test", context, targetType);

        // Calculate L2 norm
        double sumSquares = 0.0;
        for (int i = 0; i < 1024; i++) {
            double val = result.get(TensorAddress.of(i));
            sumSquares += val * val;
        }
        double norm = Math.sqrt(sumSquares);

        // Should be normalized to ~1.0
        assertEquals(1.0, norm, 0.01);

        embedder.deconstruct();
    }

    @Test
    public void testRealAPISemanticSimilarity() {
        String apiKey = System.getenv("VOYAGE_API_KEY");

        VoyageAIEmbedder embedder = createEmbedder(apiKey, "voyage-3");

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
        String apiKey = System.getenv("VOYAGE_API_KEY");

        VoyageAIEmbedder embedder = createEmbedder(apiKey, "voyage-3");

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
        String apiKey = System.getenv("VOYAGE_API_KEY");

        VoyageAIEmbedder embedder = createEmbedder(apiKey, "voyage-3");

        TensorType targetType = TensorType.fromSpec("tensor<float>(d0[1024])");
        Embedder.Context context = new Embedder.Context("integration-test");

        // Very long text (will be truncated by API)
        String longText = "This is a test. ".repeat(1000);
        Tensor result = embedder.embed(longText, context, targetType);

        assertNotNull(result);
        assertEquals(1024, result.size());

        embedder.deconstruct();
    }

    // ===== Helper Methods =====

    private VoyageAIEmbedder createEmbedder(String apiKey, String model) {
        VoyageAiEmbedderConfig.Builder configBuilder = new VoyageAiEmbedderConfig.Builder();
        configBuilder.apiKeySecretName("test_key");
        configBuilder.model(model);
        configBuilder.timeout(30000);

        return new VoyageAIEmbedder(
                configBuilder.build(),
                Embedder.Runtime.testInstance(),
                createSecrets(apiKey)
        );
    }

    private Secrets createSecrets(String apiKey) {
        return key -> () -> apiKey;
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
}
