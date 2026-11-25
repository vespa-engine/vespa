// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.embedding;

import ai.vespa.embedding.config.GgufEmbedderConfig;
import ai.vespa.modelintegration.llamacpp.LlamacppEvaluator;
import ai.vespa.modelintegration.llamacpp.LlamacppOptions;
import ai.vespa.modelintegration.llamacpp.LlamacppRuntime;
import com.yahoo.config.ModelReference;
import com.yahoo.language.process.Embedder;
import com.yahoo.language.process.InvalidInputException;
import com.yahoo.language.process.InvocationContext;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests GgufEmbedder with a fake llama.cpp runtime, i.e. what it does regardless of where the model runs.
 *
 * @author ingridhabrekke
 */
class GgufEmbedderRuntimeTest {

    private static final String MODEL = "models/embedder.gguf";
    private static final TensorType TYPE = TensorType.fromSpec("tensor<float>(x[3])");

    @Test
    void config_maps_to_llamacpp_options() {
        var runtime = new FakeRuntime();
        var config = config().parallel(4).contextSize(8192).logicalMaxBatchSize(2048).physicalMaxBatchSize(1024)
                .gpuLayers(99).poolingType(GgufEmbedderConfig.PoolingType.Enum.LAST).seed(42).threads(8)
                .batchThreads(6).continuousBatching(true);
        new GgufEmbedder(config.build(), ModelReference::value, runtime).deconstruct();

        assertEquals(MODEL, runtime.modelPath);
        assertEquals(new LlamacppOptions(4, 8192, 2048, 1024, 99, Optional.of(LlamacppOptions.Pooling.LAST), 42, 8, 6, true),
                     runtime.options);
        assertTrue(runtime.evaluator.closed);
    }

    @Test
    void default_config_leaves_llamacpp_defaults() {
        var runtime = new FakeRuntime();
        new GgufEmbedder(config().build(), ModelReference::value, runtime).deconstruct();

        assertEquals(new LlamacppOptions(1, 0, -1, -1, 0, Optional.empty(), -1, 0, 0, false), runtime.options);
    }

    @Test
    void embeds_the_prepended_text_and_normalizes_if_configured() {
        var runtime = new FakeRuntime();
        var embedder = new GgufEmbedder(config().prependQuery("query: ").normalize(true).build(), ModelReference::value, runtime);
        var context = new Embedder.Context("query(q)");

        var embedding = embedder.embed("hello", context, TYPE);

        assertEquals(List.of("query: hello"), runtime.evaluator.embedded);
        assertEquals(Tensor.from(TYPE, "[0.6, 0.0, 0.8]"), embedding);
    }

    @Test
    void long_prompts_are_truncated_to_max_prompt_tokens_without_start_and_end_tokens() {
        var runtime = new FakeRuntime();
        var embedder = new GgufEmbedder(config().maxPromptTokens(5).build(), ModelReference::value, runtime);

        embedder.embed("a b c d e f", new Embedder.Context("test"), TYPE);

        assertEquals(List.of("a b c"), runtime.evaluator.embedded);
    }

    @Test
    void token_ids_come_from_the_model() {
        var embedder = new GgufEmbedder(config().build(), ModelReference::value, new FakeRuntime());
        var context = new Embedder.Context("test");

        var tokens = embedder.embed("a b", context);

        assertEquals(List.of(97, 98), tokens);
        assertEquals("a b", embedder.decode(tokens, context));
    }

    @Test
    void request_deadline_is_passed_to_the_runtime() {
        var runtime = new FakeRuntime();
        var embedder = new GgufEmbedder(config().build(), ModelReference::value, runtime);
        var deadline = InvocationContext.Deadline.of(Duration.ofSeconds(10));

        embedder.embed("text", new Embedder.Context("test").setDeadline(deadline), TYPE);
        embedder.embed("text", new Embedder.Context("test"), TYPE);

        assertEquals(List.of(Optional.of(deadline.asInstant()), Optional.empty()), runtime.evaluator.deadlines);
    }

    @Test
    void invalid_input_keeps_its_type() {
        var runtime = new FakeRuntime();
        runtime.evaluator.failure = new InvalidInputException("too long");
        var embedder = new GgufEmbedder(config().build(), ModelReference::value, runtime);

        assertThrows(IllegalArgumentException.class, () -> embedder.embed("text", new Embedder.Context("test"), TYPE));
    }

    @Test
    void other_failures_are_gguf_embedder_exceptions() {
        var runtime = new FakeRuntime();
        runtime.evaluator.failure = new RuntimeException("engine failed");
        var embedder = new GgufEmbedder(config().build(), ModelReference::value, runtime);

        var e = assertThrows(GgufEmbedder.Exception.class, () -> embedder.embed("text", new Embedder.Context("test"), TYPE));
        assertEquals("engine failed", e.getMessage());
    }

    private static GgufEmbedderConfig.Builder config() {
        return new GgufEmbedderConfig.Builder().embeddingModel(ModelReference.valueOf(MODEL));
    }

    private static class FakeRuntime implements LlamacppRuntime {

        final FakeEvaluator evaluator = new FakeEvaluator();
        String modelPath;
        LlamacppOptions options;

        @Override
        public LlamacppEvaluator evaluatorOf(String modelPath, LlamacppOptions options) {
            this.modelPath = modelPath;
            this.options = options;
            return evaluator;
        }

    }

    // Tokenizes each space-separated word to the code point of its first character.
    private static class FakeEvaluator implements LlamacppEvaluator {

        final List<String> embedded = new ArrayList<>();
        final List<Optional<Instant>> deadlines = new ArrayList<>();
        RuntimeException failure;
        boolean closed;

        @Override
        public float[] embed(String text, Optional<Instant> deadline) {
            if (failure != null) throw failure;
            embedded.add(text);
            deadlines.add(deadline);
            return new float[] { 3, 0, 4 };
        }

        @Override
        public int[] tokenize(String text, Optional<Instant> deadline) {
            return Arrays.stream(text.split(" ")).mapToInt(word -> word.codePointAt(0)).toArray();
        }

        @Override
        public String detokenize(int[] tokens, Optional<Instant> deadline) {
            return Arrays.stream(tokens).mapToObj(Character::toString).collect(Collectors.joining(" "));
        }

        @Override
        public void close() { closed = true; }

    }

}
