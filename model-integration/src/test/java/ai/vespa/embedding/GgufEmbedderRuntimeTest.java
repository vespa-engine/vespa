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
import java.util.Set;
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
    private static final String MMPROJ = "models/mmproj.gguf";
    private static final Embedder.Image IMAGE = new Embedder.Image(new byte[] { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1 });
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

    @Test
    void projector_and_image_tokens_go_to_the_runtime() {
        var runtime = new FakeRuntime();
        new GgufEmbedder(config().mmproj(new GgufEmbedderConfig.Mmproj.Builder().model(ModelReference.valueOf(MMPROJ)))
                                 .imageMinTokens(250).imageMaxTokens(280).build(),
                         ModelReference::value, runtime).deconstruct();

        assertEquals(Optional.of(MMPROJ), runtime.mmprojPath);
        assertEquals(250, runtime.options.imageMinTokens());
        assertEquals(280, runtime.options.imageMaxTokens());
    }

    @Test
    void embeds_text_only_without_a_projector() {
        var embedder = new GgufEmbedder(config().build(), ModelReference::value, new FakeRuntime());

        assertEquals(Set.of(Set.of(Embedder.Modality.TEXT)), embedder.supportedModalities());
        var e = assertThrows(IllegalArgumentException.class,
                             () -> embedder.embed(Embedder.Input.of(IMAGE), new Embedder.Context("product.embedding"), TYPE));
        assertEquals("This GGUF embedder accepts text, but is given image", e.getMessage());
    }

    @Test
    void projector_is_not_resolved_for_a_runtime_which_does_not_load_it() {
        var runtime = new FakeRuntime();
        runtime.loadsMmproj = false;
        var resolved = new ArrayList<String>();
        var embedder = new GgufEmbedder(withProjector().build(), reference -> { resolved.add(reference.value().toString()); return reference.value(); },
                                        runtime);

        assertEquals(List.of(MODEL), resolved);
        assertEquals(Optional.empty(), runtime.mmprojPath);
        assertEquals(Set.of(Set.of(Embedder.Modality.TEXT)), embedder.supportedModalities());
    }

    @Test
    void image_first_layout_reads_the_prefix_then_the_image_then_the_text() {
        var runtime = new FakeRuntime();
        var embedder = new GgufEmbedder(withProjector().prependDocument("passage: ").build(), ModelReference::value, runtime);
        var document = new Embedder.Context("product.embedding");

        assertEquals(Set.of(Set.of(Embedder.Modality.TEXT), Set.of(Embedder.Modality.IMAGE),
                            Set.of(Embedder.Modality.TEXT, Embedder.Modality.IMAGE)),
                     embedder.supportedModalities());
        assertEquals(List.of(text("passage: "), image(), text(" A title")),
                     embedder.parts(Embedder.Input.of("A title", IMAGE), document));
        assertEquals(List.of(text("passage: "), image()), embedder.parts(Embedder.Input.of(IMAGE), document));

        assertEquals(Tensor.from(TYPE, "[3, 0, 4]"), embedder.embed(Embedder.Input.of("A title", IMAGE), document, TYPE));
        assertEquals(List.of(List.of(text("passage: "), image(), text(" A title"))), runtime.evaluator.embeddedParts);
    }

    @Test
    void text_first_layout_reads_the_text_then_the_image() {
        var embedder = new GgufEmbedder(withProjector().imageFirst(false).prependQuery("query: ").build(),
                                        ModelReference::value, new FakeRuntime());

        assertEquals(List.of(text("A title "), image()),
                     embedder.parts(Embedder.Input.of("A title", IMAGE), new Embedder.Context("product.embedding")));
        assertEquals(List.of(text("query: A title "), image()),
                     embedder.parts(Embedder.Input.of("A title", IMAGE), new Embedder.Context("query(q)")));
        assertEquals(List.of(image()), embedder.parts(Embedder.Input.of(IMAGE), new Embedder.Context("product.embedding")));
    }

    @Test
    void max_prompt_tokens_truncates_the_text_alone_with_an_image() {
        var embedder = new GgufEmbedder(withProjector().maxPromptTokens(4).prependDocument("passage: ").build(),
                                        ModelReference::value, new FakeRuntime());

        assertEquals(List.of(text("passage: "), image(), text(" a b")),
                     embedder.parts(Embedder.Input.of("a b c d e", IMAGE), new Embedder.Context("product.embedding")));
    }

    private static GgufEmbedderConfig.Builder withProjector() {
        return config().mmproj(new GgufEmbedderConfig.Mmproj.Builder().model(ModelReference.valueOf(MMPROJ)));
    }

    private static LlamacppEvaluator.Part text(String text) { return new LlamacppEvaluator.Part.Text(text); }

    private static LlamacppEvaluator.Part image() { return new LlamacppEvaluator.Part.Image(IMAGE); }

    private static GgufEmbedderConfig.Builder config() {
        return new GgufEmbedderConfig.Builder().embeddingModel(ModelReference.valueOf(MODEL));
    }

    private static class FakeRuntime implements LlamacppRuntime {

        final FakeEvaluator evaluator = new FakeEvaluator();
        String modelPath;
        Optional<String> mmprojPath;
        LlamacppOptions options;
        boolean loadsMmproj = true;

        @Override
        public boolean loadsMmproj() { return loadsMmproj; }

        @Override
        public LlamacppEvaluator evaluatorOf(String modelPath, LlamacppOptions options) {
            return evaluatorOf(modelPath, Optional.empty(), options);
        }

        @Override
        public LlamacppEvaluator evaluatorOf(String modelPath, Optional<String> mmprojPath, LlamacppOptions options) {
            this.modelPath = modelPath;
            this.mmprojPath = mmprojPath;
            this.options = options;
            evaluator.acceptsImages = mmprojPath.isPresent();
            return evaluator;
        }

    }

    // Tokenizes each space-separated word to the code point of its first character.
    private static class FakeEvaluator implements LlamacppEvaluator {

        final List<String> embedded = new ArrayList<>();
        final List<List<Part>> embeddedParts = new ArrayList<>();
        boolean acceptsImages;
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
        public boolean acceptsImages() { return acceptsImages; }

        @Override
        public float[] embed(List<Part> parts, Optional<Instant> deadline) {
            embeddedParts.add(parts);
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
