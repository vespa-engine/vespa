// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.triton;

import ai.vespa.llm.clients.TritonConfig;
import ai.vespa.modelintegration.evaluator.EmbeddedOnnxRuntime;
import ai.vespa.modelintegration.evaluator.OnnxEvaluator;
import ai.vespa.modelintegration.evaluator.OnnxEvaluatorOptions;
import ai.vespa.modelintegration.evaluator.OnnxRuntime;
import ai.vespa.modelintegration.evaluator.OnnxPadder;
import ai.vespa.modelintegration.evaluator.config.OnnxEvaluatorConfig;
import ai.vespa.embedding.BertBaseEmbedder;
import ai.vespa.embedding.ColBertEmbedder;
import ai.vespa.embedding.HuggingFaceEmbedder;
import ai.vespa.embedding.SpladeEmbedder;
import com.yahoo.config.ModelReference;
import com.yahoo.embedding.BertBaseEmbedderConfig;
import com.yahoo.embedding.ColBertEmbedderConfig;
import com.yahoo.embedding.SpladeEmbedderConfig;
import com.yahoo.embedding.huggingface.HuggingFaceEmbedderConfig;
import com.yahoo.language.process.Embedder;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Runs the embedders through the Triton runtime with padding buckets, and checks that they embed the same
 * as with the embedded runtime, with padding in use. This is the end-to-end test of padding short of a deployment.
 *
 * @author glebashnik
 */
@EnabledIfSystemProperty(named = "VESPA_USE_TRITON", matches = "true")
@ExtendWith(ContainerEnvironmentAvailableCondition.class)
class TritonPaddedEmbeddingTest {

    private static final String MODELS = "src/test/models/onnx/transformer/";
    private static final String TOKENIZER = MODELS + "real_tokenizer.json";
    /** A float model with BERT-like masked attention, whose output for the real tokens is unchanged by padding */
    private static final String MASKED_MODEL = MODELS + "dummy_masked_attention_embedder.onnx";
    private static final String TEXT = "This is a test with lots of input text here";

    private static TritonServerContainer tritonContainer;

    @BeforeAll
    static void startTriton() throws IOException {
        tritonContainer = new TritonServerContainer();
        tritonContainer.start();
    }

    @AfterAll
    static void stopTriton() {
        tritonContainer.stop();
    }

    @Test
    void huggingFaceEmbedsTheSameThroughTritonWithPadding() {
        for (var pooling : HuggingFaceEmbedderConfig.PoolingStrategy.Enum.values()) {
            if (pooling == HuggingFaceEmbedderConfig.PoolingStrategy.Enum.none) continue; // Needs a model output without tokens
            var config = new HuggingFaceEmbedderConfig.Builder()
                    .tokenizerPath(ModelReference.valueOf(TOKENIZER))
                    .transformerModel(ModelReference.valueOf(MASKED_MODEL))
                    .poolingStrategy(pooling)
                    .build();
            assertEmbedsTheSame(runtime -> new HuggingFaceEmbedder(runtime, Embedder.Runtime.testInstance(), config, onnxConfig(runtime), new ModelPaths()),
                                List.of("tensor<float>(x[16])", "tensor<int8>(x[2])"), "schema.indexing");
        }
    }

    /** With runtime pooling, Vespa pads the inputs and Triton pools them. The padding must stay out of the embedding. */
    @Test
    void huggingFaceEmbedsTheSameThroughTritonWithPaddingAndPooling() {
        assumeTrue(tritonContainer.isVespaImage(), "Needs a Vespa Triton image that supports pooling");
        for (var pooling : HuggingFaceEmbedderConfig.PoolingStrategy.Enum.values()) {
            if (pooling == HuggingFaceEmbedderConfig.PoolingStrategy.Enum.none) continue; // Needs a model output without tokens
            var config = new HuggingFaceEmbedderConfig.Builder()
                    .tokenizerPath(ModelReference.valueOf(TOKENIZER))
                    .transformerModel(ModelReference.valueOf(MASKED_MODEL))
                    .poolingStrategy(pooling)
                    .build();
            assertEmbedsTheSame(runtime -> new HuggingFaceEmbedder(runtime, Embedder.Runtime.testInstance(), config, onnxConfig(runtime), new ModelPaths()),
                                List.of("tensor<float>(x[16])", "tensor<int8>(x[2])"), "schema.indexing", TEXT, true);
        }
    }

    @Test
    void bertBaseEmbedsTheSameThroughTritonWithPadding() {
        var config = new BertBaseEmbedderConfig.Builder()
                .tokenizerVocab(ModelReference.valueOf(MODELS + "dummy_vocab.txt"))
                .transformerModel(ModelReference.valueOf(MASKED_MODEL))
                .transformerTokenTypeIds("")
                .transformerOutput("last_hidden_state")
                .build();
        assertEmbedsTheSame(runtime -> new BertBaseEmbedder(runtime, Embedder.Runtime.testInstance(), config, onnxConfig(runtime)),
                            List.of("tensor<float>(x[16])"), "schema.indexing", "a b c d e f g h i j k"); // The dummy vocabulary has single letters
    }

    @Test
    void spladeEmbedsTheSameThroughTritonWithPadding() {
        var config = new SpladeEmbedderConfig.Builder()
                .tokenizerPath(ModelReference.valueOf(TOKENIZER))
                .transformerModel(ModelReference.valueOf(MODELS + "dummy_transformer_mlm.onnx"))
                .termScoreThreshold(1.15)
                .build();
        assertEmbedsTheSame(runtime -> new SpladeEmbedder(runtime, Embedder.Runtime.testInstance(), config, onnxConfig(runtime)),
                            List.of("tensor<float>(t{})"), "schema.indexing");
    }

    @Test
    void colBertEmbedsTheSameThroughTritonWithPadding() {
        var config = new ColBertEmbedderConfig.Builder()
                .tokenizerPath(ModelReference.valueOf(TOKENIZER))
                .transformerModel(ModelReference.valueOf(MASKED_MODEL))
                .transformerOutput("last_hidden_state")
                .build();
        for (var context : List.of("schema.indexing", "query"))
            assertEmbedsTheSame(runtime -> new ColBertEmbedder(runtime, Embedder.Runtime.testInstance(), config, onnxConfig(runtime)),
                                List.of("tensor<float>(dt{},x[16])", "tensor<int8>(dt{},x[2])"), context);
    }

    private static void assertEmbedsTheSame(Function<OnnxRuntime, Embedder> embedderOf, List<String> types, String context) {
        assertEmbedsTheSame(embedderOf, types, context, TEXT);
    }

    /** Embeds with the Triton runtime, padded, and with the embedded runtime, and compares. */
    private static void assertEmbedsTheSame(Function<OnnxRuntime, Embedder> embedderOf, List<String> types, String context, String text) {
        assertEmbedsTheSame(embedderOf, types, context, text, false);
    }

    /** Embeds with the Triton runtime, padded, and pooled by Triton if asked, then with the embedded runtime, and compares. */
    private static void assertEmbedsTheSame(Function<OnnxRuntime, Embedder> embedderOf, List<String> types, String context, String text, boolean pooling) {
        var triton = new RecordingTritonRuntime(pooling);
        try {
            var padded = embedderOf.apply(triton);
            var unpadded = embedderOf.apply(EmbeddedOnnxRuntime.createTestInstance());
            for (var type : types) {
                var targetType = TensorType.fromSpec(type);
                assertClose(unpadded.embed(text, new Embedder.Context(context), targetType),
                            padded.embed(text, new Embedder.Context(context), targetType));
            }
            assertEquals(1, triton.evaluators.size());
            assertTrue(triton.evaluators.get(0) instanceof OnnxPadder, "Padding should be verified and in use");
            assertEquals(pooling, triton.evaluators.get(0).runtimePooling().isPresent(), "Pooled by Triton when asked, through the padding");
        } finally {
            triton.deconstruct();
        }
    }

    /** Buckets, and the batching that makes padding worthwhile. The GPU is the default, and Triton falls back to CPU without one. */
    private static OnnxEvaluatorConfig onnxConfig(OnnxRuntime runtime) {
        var builder = new OnnxEvaluatorConfig.Builder();
        if (runtime instanceof TritonOnnxRuntime) builder.batching.maxSize(8).paddingBuckets(List.of(16, 64));
        return builder.build();
    }

    private static void assertClose(Tensor expected, Tensor actual) {
        assertEquals(expected.type(), actual.type());
        assertEquals(expected.size(), actual.size(), "Cells of " + expected + " and " + actual);
        expected.cellIterator().forEachRemaining(cell -> {
            double value = actual.get(cell.getKey());
            assertTrue(Math.abs(cell.getValue() - value) <= 1e-4, "Expected " + cell.getValue() + " at " + cell.getKey() + ", got " + value);
        });
    }

    /** The Triton runtime against the test container, keeping the evaluators it creates so that their padding can be inspected */
    private static class RecordingTritonRuntime extends TritonOnnxRuntime {

        final List<OnnxEvaluator> evaluators = new ArrayList<>();

        RecordingTritonRuntime(boolean pooling) {
            super(new TritonConfig.Builder()
                          .target(tritonContainer.getGrpcEndpoint())
                          .modelControlMode(TritonConfig.ModelControlMode.EXPLICIT)
                          .modelRepositoryPath(tritonContainer.getModelRepositoryPath().toString())
                          .onnxPooling(pooling)
                          .build());
        }

        @Override
        public OnnxEvaluator evaluatorOf(String modelPath, OnnxEvaluatorOptions options) {
            var evaluator = super.evaluatorOf(modelPath, options);
            evaluators.add(evaluator);
            return evaluator;
        }

    }

    private static class ModelPaths implements ai.vespa.modelintegration.utils.ModelPathHelper {
        @Override
        public Path getModelPathResolvingIfNecessary(ModelReference modelReference) { return modelReference.value(); }
    }

}
