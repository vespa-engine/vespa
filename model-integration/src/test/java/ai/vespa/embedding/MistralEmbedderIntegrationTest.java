// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.embedding;

import ai.vespa.embedding.config.MistralEmbedderConfig;
import com.yahoo.language.process.Embedder;
import com.yahoo.tensor.TensorType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.time.Duration;
import java.util.List;

import static ai.vespa.embedding.EmbedderTestUtils.assertNonZeroTensor;
import static ai.vespa.embedding.EmbedderTestUtils.cosineSimilarity;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for Mistral embedder with the real API.
 *
 * @author bjorncs
 */
@EnabledIfEnvironmentVariable(named = "VESPA_TEST_MISTRAL_API_KEY", matches = ".+")
public class MistralEmbedderIntegrationTest {

    @Test
    public void testEmbedding() {
        var embedder = createEmbedder("mistral-embed", 1024, MistralEmbedderConfig.Quantization.Enum.AUTO);
        var targetType = TensorType.fromSpec("tensor<float>(d0[1024])");
        var context = new Embedder.Context("integration-test");

        var result = embedder.embed("Hello from Mistral", context, targetType);
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
        var embedder = createEmbedder("mistral-embed", 1024, MistralEmbedderConfig.Quantization.Enum.AUTO);
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
    public void testCodestralEmbedWithReducedDimensions() {
        // codestral-embed supports configurable dimensions (unlike mistral-embed)
        var embedder = createEmbedder("codestral-embed", 512, MistralEmbedderConfig.Quantization.Enum.AUTO);
        var targetType = TensorType.fromSpec("tensor<float>(d0[512])");
        var context = new Embedder.Context("integration-test");

        var result = embedder.embed("def hello(): print('world')", context, targetType);
        assertNotNull(result);
        assertEquals(512, result.size());
        assertNonZeroTensor(result);

        embedder.deconstruct();
    }

    @Test
    public void testCodestralEmbedWithInt8Quantization() {
        var embedder = createEmbedder("codestral-embed", 2048, MistralEmbedderConfig.Quantization.Enum.INT8);
        var targetType = TensorType.fromSpec("tensor<int8>(d0[2048])");
        var context = new Embedder.Context("integration-test");

        var result = embedder.embed("Testing int8 quantization on codestral-embed", context, targetType);
        assertNotNull(result);
        assertEquals(2048, result.size());
        assertEquals(targetType, result.type());
        assertNonZeroTensor(result);

        embedder.deconstruct();
    }

    @Test
    public void testCodestralEmbedWithBinaryQuantization() {
        // Binary quantization returns INT8 tensor with 1/8 the configured dimensions
        var embedder = createEmbedder("codestral-embed", 1024, MistralEmbedderConfig.Quantization.Enum.BINARY);
        var targetType = TensorType.fromSpec("tensor<int8>(d0[128])");
        var context = new Embedder.Context("integration-test");

        var result = embedder.embed("Testing binary quantization", context, targetType);
        assertNotNull(result);
        assertEquals(128, result.size());
        assertEquals(targetType, result.type());
        assertNonZeroTensor(result);

        embedder.deconstruct();
    }

    @Test
    public void testBatchingConfig() {
        var config = new MistralEmbedderConfig.Builder()
                .apiKeySecretRef("test_key")
                .model("mistral-embed")
                .dimensions(1024)
                .quantization(MistralEmbedderConfig.Quantization.Enum.AUTO);
        config.batching.maxSize(16).maxDelayMillis(200);
        var embedder = new MistralEmbedder(config.build(), Embedder.Runtime.testInstance(),
                key -> () -> System.getenv("VESPA_TEST_MISTRAL_API_KEY"));

        var batching = embedder.batchingConfig();
        assertTrue(batching.isEnabled());
        assertEquals(16, batching.maxSize());
        assertEquals(Duration.ofMillis(200), batching.maxDelay());

        var targetType = TensorType.fromSpec("tensor<float>(d0[1024])");
        var context = new Embedder.Context("integration-test");
        var results = embedder.embed(List.of("First text", "Second text"), context, targetType);
        assertEquals(2, results.size());
        for (var result : results) {
            assertEquals(1024, result.size());
            assertNonZeroTensor(result);
        }

        embedder.deconstruct();
    }

    private static MistralEmbedder createEmbedder(String model, int dimensions, MistralEmbedderConfig.Quantization.Enum quantization) {
        var config = new MistralEmbedderConfig.Builder()
                .apiKeySecretRef("test_key")
                .model(model)
                .dimensions(dimensions)
                .quantization(quantization)
                .build();
        return new MistralEmbedder(config, Embedder.Runtime.testInstance(),
                key -> () -> System.getenv("VESPA_TEST_MISTRAL_API_KEY"));
    }
}
