// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.embedding;

import ai.vespa.embedding.config.GgufEmbedderConfig;
import com.yahoo.config.ModelReference;
import com.yahoo.language.process.Embedder;
import com.yahoo.tensor.TensorType;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author bjorncs
 */
class GgufEmbedderTest {

    private static final String TINY_LLM_MODEL = "src/test/models/llm/tinyllm.gguf";
    private static final Embedder.Context DUMMY_CONTEXT = new Embedder.Context("test");

    @Test
    void produces_correct_embedding() {
        var config = new GgufEmbedderConfig.Builder()
                .embeddingModel(ModelReference.valueOf(TINY_LLM_MODEL))
                .seed(1)
                .build();
        var embedder = new GgufEmbedder(config, ModelReference::value);
        String text = "This is a test";
        var tensorType = TensorType.fromSpec("tensor<float>(x[64])");
        try {
            var embedding = embedder.embed(text, DUMMY_CONTEXT, tensorType);
            assertNotNull(embedding);
            assertEquals(tensorType, embedding.type());
            assertTrue(embedding.sum().asDouble() != 0.0);
            double[] expectedValues = {
                    1.0292758, -1.3270985, 0.18010023, -1.7205194, 0.104211554, -5.9496317, 1.6353445, 0.37937796,
                    0.7343295, -0.46375245, 1.208143, -1.4408318, -1.3857322, 0.0313699, -1.4294251, -9.711142,
                    -1.1832366, -2.1676993, 0.45259798, -3.6219897, 1.0608853, -0.53369373, 0.1757773, 5.515418,
                    -1.0470327, -0.39121142, -0.6377803, 0.597403, -0.34900287, -1.0746356, 3.792547, 3.5979981,
                    1.6292435, 1.067964, 0.2716063, -2.0164044, 3.003206, 1.1531613, 0.22300358, 3.1061413, 2.0321712,
                    -0.025435379, 0.42662904, -0.17282704, 0.85300887, 0.5182117, -0.25126216, 0.68381417, 0.014906612,
                    1.3040913, 0.07799442, 0.63696116, 2.3029523, -2.0566816, 1.2332673, 0.764334, -0.0061218967,
                    -1.5629356, -0.14813207, -0.6894363, -1.5513821, 1.2363527, -1.350878, -1.6226838};
            double[] actualValues = new double[embedding.sizeAsInt()];
            var valueIterator = embedding.valueIterator();
            for (int i = 0; i < actualValues.length; i++) {
                actualValues[i] = valueIterator.next();
            }
            assertArrayEquals(expectedValues, actualValues, 1e-5);
        } finally {
            assertDoesNotThrow(embedder::deconstruct);
        }
    }

    @Test
    void produces_normalized_embedding() {
        var config = new GgufEmbedderConfig.Builder()
                .embeddingModel(ModelReference.valueOf(TINY_LLM_MODEL))
                .seed(1)
                .normalize(true)
                .build();
        var embedder = new GgufEmbedder(config, ModelReference::value);
        String text = "This is a test";
        try {
            var tensorType = TensorType.fromSpec("tensor<float>(x[64])");
            var embedding = embedder.embed(text, DUMMY_CONTEXT, tensorType);
            assertNotNull(embedding);
            assertEquals(tensorType, embedding.type());
            assertEquals(1.0, embedding.multiply(embedding).sum().asDouble(), 1e-5);
        } finally {
            assertDoesNotThrow(embedder::deconstruct);
        }
    }

    @Test
    void produces_correct_token_ids() {
        var config = new GgufEmbedderConfig.Builder()
                .embeddingModel(ModelReference.valueOf(TINY_LLM_MODEL))
                .seed(1)
                .build();
        var embedder = new GgufEmbedder(config, ModelReference::value);
        String text = "This is a test";
        try {
            var tokens = embedder.embed(text, DUMMY_CONTEXT);
            assertEquals(9, tokens.size());
            assertEquals(List.of(274, 415, 293, 410, 293, 261, 259, 411, 356), tokens);
            assertEquals(" This is a test", embedder.decode(tokens, DUMMY_CONTEXT));
        } finally {
            assertDoesNotThrow(embedder::deconstruct);
        }
    }

    @Nested
    class LargePrompts {
        private static final String LARGE_PROMPT = "This is a test. ".repeat(100);

        @Test
        void succeeds_when_overriding_batch_and_context_size() {
            var config = new GgufEmbedderConfig.Builder()
                    .embeddingModel(ModelReference.valueOf(TINY_LLM_MODEL))
                    .physicalMaxBatchSize(2*1024)
                    .logicalMaxBatchSize(2*1024)
                    .contextSize(2*1024)
                    .seed(1)
                    .build();
            var embedder = new GgufEmbedder(config, ModelReference::value);
            try {
                var tokens = embedder.embed(LARGE_PROMPT, DUMMY_CONTEXT);
                assertEquals(1001, tokens.size());
                var tensorType = TensorType.fromSpec("tensor<float>(x[64])");
                var embedding = embedder.embed(LARGE_PROMPT, DUMMY_CONTEXT, tensorType);

                assertNotNull(embedding);
                assertEquals(tensorType, embedding.type());
                assertTrue(embedding.sum().asDouble() != 0.0);
            } finally {
                assertDoesNotThrow(embedder::deconstruct);
            }
        }

        @Test
        void succeeds_when_overriding_max_tokens() {
            var config = new GgufEmbedderConfig.Builder()
                    .embeddingModel(ModelReference.valueOf(TINY_LLM_MODEL))
                    .maxPromptTokens(128)
                    .seed(1)
                    .build();
            var embedder = new GgufEmbedder(config, ModelReference::value);
            try {
                var tokens = embedder.embed(LARGE_PROMPT, DUMMY_CONTEXT);
                assertEquals(1001, tokens.size());
                var tensorType = TensorType.fromSpec("tensor<float>(x[64])");
                var embedding = embedder.embed(LARGE_PROMPT, DUMMY_CONTEXT, tensorType);

                assertNotNull(embedding);
                assertEquals(tensorType, embedding.type());
                assertTrue(embedding.sum().asDouble() != 0.0);
            } finally {
                assertDoesNotThrow(embedder::deconstruct);
            }
        }

        @Test
        void fails_with_default_config() {
            var config = new GgufEmbedderConfig.Builder()
                    .embeddingModel(ModelReference.valueOf(TINY_LLM_MODEL))
                    .seed(1)
                    .build();
            var embedder = new GgufEmbedder(config, ModelReference::value);
            try {
                var message = assertThrows(IllegalArgumentException.class, () ->
                    embedder.embed(LARGE_PROMPT, DUMMY_CONTEXT, TensorType.fromSpec("tensor<float>(x[1024])")));
                assertEquals(
                        "Input text is too large (prompt UTF-16 length: 1600). Either set max prompt tokens or adjust batch/context size.",
                        message.getMessage());
            } finally {
                assertDoesNotThrow(embedder::deconstruct);
            }
        }
    }
}
