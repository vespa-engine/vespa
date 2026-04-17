// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.embedding;

import ai.vespa.embedding.config.OpenaiEmbedderConfig;
import com.yahoo.language.process.Embedder;
import com.yahoo.tensor.TensorType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.List;

import static ai.vespa.embedding.EmbedderTestUtils.assertNonZeroTensor;
import static ai.vespa.embedding.EmbedderTestUtils.cosineSimilarity;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for OpenAI embedder with real API.
 *
 * @author bjorncs
 */
@EnabledIfEnvironmentVariable(named = "VESPA_TEST_OPENAI_API_KEY", matches = "sk-.+")
public class OpenAIEmbedderIntegrationTest {

    @Test
    public void testEmbedding() {
        var embedder = createEmbedder(1024);
        var targetType = TensorType.fromSpec("tensor<float>(d0[1024])");
        var context = new Embedder.Context("integration-test");

        var result = embedder.embed("Hello, this is a test.", context, targetType);
        assertNotNull(result);
        assertEquals(1024, result.size());
        assertEquals(targetType, result.type());
        assertNonZeroTensor(result);

        var embedding1 = embedder.embed("The cat sits on the mat", context, targetType);
        var embedding2 = embedder.embed("A feline rests on the rug", context, targetType);
        var embedding3 = embedder.embed("The quick brown fox jumps", context, targetType);

        double similarity12 = cosineSimilarity(embedding1, embedding2);
        double similarity13 = cosineSimilarity(embedding1, embedding3);
        assertTrue(similarity12 > similarity13,
                "Similar texts should have higher similarity. Sim(1,2)=%f, Sim(1,3)=%f"
                        .formatted(similarity12, similarity13));

        embedder.deconstruct();
    }

    @Test
    public void testBatchEmbedding() {
        var embedder = createEmbedder(1024);
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

        double sim02 = cosineSimilarity(results.get(0), results.get(2));
        double sim01 = cosineSimilarity(results.get(0), results.get(1));
        assertTrue(sim02 > sim01,
                "ML texts should be more similar. Sim(0,2)=%f, Sim(0,1)=%f".formatted(sim02, sim01));

        embedder.deconstruct();
    }

    @Test
    public void testReducedDimensions() {
        var embedder = createEmbedder(512);
        var targetType = TensorType.fromSpec("tensor<float>(d0[512])");
        var context = new Embedder.Context("integration-test");
        var result = embedder.embed("Testing reduced dimensions", context, targetType);

        assertNotNull(result);
        assertEquals(512, result.size());
        assertNonZeroTensor(result);

        embedder.deconstruct();
    }

    /**
     * Verifies that the legacy text-embedding-ada-002 model (fixed 1536 output size, does
     * not accept the "dimensions" request field) works when configured with the expected
     * dimensions=1536. The embedder must omit the field from the request for this model.
     */
    @Test
    public void testAda002WithoutDimensionsField() {
        var config = new OpenaiEmbedderConfig.Builder()
                .apiKeySecretRef("test_key")
                .model("text-embedding-ada-002")
                .dimensions(1536)
                .build();
        var embedder = new OpenAIEmbedder(config, Embedder.Runtime.testInstance(),
                key -> () -> System.getenv("VESPA_TEST_OPENAI_API_KEY"));
        try {
            var targetType = TensorType.fromSpec("tensor<float>(d0[1536])");
            var context = new Embedder.Context("integration-test");
            var result = embedder.embed("hello", context, targetType);
            assertNotNull(result);
            assertEquals(1536, result.size());
            assertEquals(targetType, result.type());
            assertNonZeroTensor(result);
        } finally {
            embedder.deconstruct();
        }
    }

    private static OpenAIEmbedder createEmbedder(int dimensions) {
        var config = new OpenaiEmbedderConfig.Builder()
                .apiKeySecretRef("test_key")
                .model("text-embedding-3-small")
                .dimensions(dimensions)
                .build();
        return new OpenAIEmbedder(config, Embedder.Runtime.testInstance(),
                key -> () -> System.getenv("VESPA_TEST_OPENAI_API_KEY"));
    }
}
