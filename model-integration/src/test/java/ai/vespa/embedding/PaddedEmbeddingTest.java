// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.embedding;

import ai.vespa.modelintegration.evaluator.OnnxEvaluator;
import ai.vespa.modelintegration.evaluator.OnnxEvaluatorOptions;
import ai.vespa.modelintegration.evaluator.OnnxRuntime;
import ai.vespa.modelintegration.evaluator.config.OnnxEvaluatorConfig;
import com.yahoo.config.ModelReference;
import com.yahoo.embedding.BertBaseEmbedderConfig;
import com.yahoo.embedding.ColBertEmbedderConfig;
import com.yahoo.embedding.SpladeEmbedderConfig;
import com.yahoo.embedding.huggingface.HuggingFaceEmbedderConfig;
import com.yahoo.language.process.Embedder;
import com.yahoo.tensor.IndexedTensor;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Verifies that embedders pad their inputs to the configured padding buckets when the evaluator benefits from it,
 * and that the padding does not change the embeddings.
 *
 * @author ingrid
 */
class PaddedEmbeddingTest {

    private static final String TOKENIZER = "src/test/models/onnx/transformer/real_tokenizer.json";
    /**
     * A float model with BERT-like masked attention, see dummy_masked_attention_embedder.py.
     * The other test models either don't mask attention, or are dynamically quantized:
     * dynamic quantization computes its scale over the whole input, so padding shifts the result slightly.
     */
    private static final String MASKED_MODEL = "src/test/models/onnx/transformer/dummy_masked_attention_embedder.onnx";
    private static final String TEXT = "This is a test with lots of input text here";
    private static final double TOLERANCE = 1e-5;

    @Test
    void huggingFaceEmbeddingIsUnchangedByPadding() {
        String model = MASKED_MODEL;
        assumeTrue(OnnxRuntime.isRuntimeAvailable(model));
        for (var pooling : HuggingFaceEmbedderConfig.PoolingStrategy.Enum.values()) {
            if (pooling == HuggingFaceEmbedderConfig.PoolingStrategy.Enum.none) continue; // Needs a model output without tokens
            var config = new HuggingFaceEmbedderConfig.Builder()
                    .tokenizerPath(ModelReference.valueOf(TOKENIZER))
                    .transformerModel(ModelReference.valueOf(model))
                    .poolingStrategy(pooling)
                    .build();
            var batching = new BatchingRuntime();
            var padded = new HuggingFaceEmbedder(batching, Embedder.Runtime.testInstance(), config, onnxConfigWithBuckets(), new HuggingFaceEmbedderTest.MockModelPathHelper());
            batching.sequenceLengths.clear(); // Drop the padder's own verification of the model
            var unpadded = new HuggingFaceEmbedder(OnnxRuntime.testInstance(), Embedder.Runtime.testInstance(), config, onnxConfig(), new HuggingFaceEmbedderTest.MockModelPathHelper());

            for (var type : List.of("tensor<float>(x[16])", "tensor<int8>(x[2])")) {
                var targetType = TensorType.fromSpec(type);
                assertClose(unpadded.embed(TEXT, context(), targetType), padded.embed(TEXT, context(), targetType));
            }
            assertPaddedToBucket(batching, 16);
        }
    }

    @Test
    void bertBaseEmbeddingIsUnchangedByPadding() {
        String model = MASKED_MODEL;
        assumeTrue(OnnxRuntime.isRuntimeAvailable(model));
        var config = new BertBaseEmbedderConfig.Builder()
                .tokenizerVocab(ModelReference.valueOf("src/test/models/onnx/transformer/dummy_vocab.txt"))
                .transformerModel(ModelReference.valueOf(model))
                .transformerTokenTypeIds("")
                .transformerOutput("last_hidden_state")
                .build();
        var batching = new BatchingRuntime();
        var padded = new BertBaseEmbedder(batching, Embedder.Runtime.testInstance(), config, onnxConfigWithBuckets());
        batching.sequenceLengths.clear(); // Drop the padder's own verification of the model
        var unpadded = new BertBaseEmbedder(OnnxRuntime.testInstance(), Embedder.Runtime.testInstance(), config, onnxConfig());

        var type = TensorType.fromSpec("tensor<float>(x[16])");
        var text = "a b c d e f g h i j k"; // The dummy vocabulary has single letters and digits only
        assertClose(unpadded.embed(text, context(), type), padded.embed(text, context(), type));
        assertPaddedToBucket(batching, 16);
    }

    @Test
    void spladeEmbeddingIsUnchangedByPadding() {
        String model = "src/test/models/onnx/transformer/dummy_transformer_mlm.onnx";
        assumeTrue(OnnxRuntime.isRuntimeAvailable(model));
        var config = new SpladeEmbedderConfig.Builder()
                .tokenizerPath(ModelReference.valueOf(TOKENIZER))
                .transformerModel(ModelReference.valueOf(model))
                .termScoreThreshold(1.15)
                .build();
        var type = TensorType.fromSpec("tensor<float>(t{})");
        for (boolean customReduce : new boolean[] { true, false }) {
            var batching = new BatchingRuntime();
            var padded = new SpladeEmbedder(batching, Embedder.Runtime.testInstance(), config, onnxConfigWithBuckets(), customReduce);
            batching.sequenceLengths.clear(); // Drop the padder's own verification of the model
            var unpadded = new SpladeEmbedder(OnnxRuntime.testInstance(), Embedder.Runtime.testInstance(), config, onnxConfig(), customReduce);
            assertClose(unpadded.embed(TEXT, context(), type), padded.embed(TEXT, context(), type));
            assertPaddedToBucket(batching, 16);
        }
    }

    @Test
    void colBertDocumentEmbeddingIsUnchangedByPadding() {
        String model = MASKED_MODEL;
        assumeTrue(OnnxRuntime.isRuntimeAvailable(model));
        var config = new ColBertEmbedderConfig.Builder()
                .tokenizerPath(ModelReference.valueOf(TOKENIZER))
                .transformerModel(ModelReference.valueOf(model))
                .transformerOutput("last_hidden_state")
                .build();
        var batching = new BatchingRuntime();
        var padded = new ColBertEmbedder(batching, Embedder.Runtime.testInstance(), config, onnxConfigWithBuckets());
        batching.sequenceLengths.clear(); // Drop the padder's own verification of the model
        var unpadded = new ColBertEmbedder(OnnxRuntime.testInstance(), Embedder.Runtime.testInstance(), config, onnxConfig());

        for (var type : List.of("tensor<float>(dt{},x[16])", "tensor<int8>(dt{},x[2])")) {
            var targetType = TensorType.fromSpec(type);
            assertClose(unpadded.embed(TEXT, context(), targetType), padded.embed(TEXT, context(), targetType));
        }
        assertPaddedToBucket(batching, 16);
    }

    @Test
    void embeddingsAreNotPaddedWithoutConfiguredBuckets() {
        String model = MASKED_MODEL;
        assumeTrue(OnnxRuntime.isRuntimeAvailable(model));
        var config = new HuggingFaceEmbedderConfig.Builder()
                .tokenizerPath(ModelReference.valueOf(TOKENIZER))
                .transformerModel(ModelReference.valueOf(model))
                .build();
        var batching = new BatchingRuntime();
        var embedder = new HuggingFaceEmbedder(batching, Embedder.Runtime.testInstance(), config, onnxConfig(), new HuggingFaceEmbedderTest.MockModelPathHelper());
        embedder.embed(TEXT, context(), TensorType.fromSpec("tensor<float>(x[16])"));
        assertTrue( ! batching.sequenceLengths.isEmpty(), "The model should have been evaluated");
        for (long length : batching.sequenceLengths) assertTrue(length < 16, "Unpadded length, got " + length);
        assertEquals(0, batching.paddingQuestions.get(), "The evaluator should not be asked without buckets");
    }

    @Test
    void paddingIsDisabledWhenItChangesTheModelOutput() {
        String model = MASKED_MODEL;
        assumeTrue(OnnxRuntime.isRuntimeAvailable(model));
        var config = new HuggingFaceEmbedderConfig.Builder()
                .tokenizerPath(ModelReference.valueOf(TOKENIZER))
                .transformerModel(ModelReference.valueOf(model))
                .build();
        var batching = new BatchingRuntime(true);
        var embedder = new HuggingFaceEmbedder(batching, Embedder.Runtime.testInstance(), config, onnxConfigWithBuckets(), new HuggingFaceEmbedderTest.MockModelPathHelper());
        assertEquals(1, batching.paddingQuestions.get(), "The evaluator should have been asked, since buckets are configured");
        assertEquals(2, batching.evaluations.get(), "The model should have been evaluated with and without padding to verify it");
        batching.sequenceLengths.clear();

        embedder.embed(TEXT, context(), TensorType.fromSpec("tensor<float>(x[16])"));
        assertTrue( ! batching.sequenceLengths.isEmpty(), "The model should have been evaluated");
        for (long length : batching.sequenceLengths) assertTrue(length < 16, "Unpadded length, got " + length);
    }

    @Test
    void bucketsLargerThanTheModelMaxTokensAreCapped() {
        String model = MASKED_MODEL;
        assumeTrue(OnnxRuntime.isRuntimeAvailable(model));
        var config = new HuggingFaceEmbedderConfig.Builder()
                .tokenizerPath(ModelReference.valueOf(TOKENIZER))
                .transformerModel(ModelReference.valueOf(model))
                .transformerMaxTokens(32)
                .build();
        var batching = new BatchingRuntime();
        var embedder = new HuggingFaceEmbedder(batching, Embedder.Runtime.testInstance(), config, onnxConfigWithBuckets(), new HuggingFaceEmbedderTest.MockModelPathHelper());
        batching.sequenceLengths.clear(); // Drop the padder's own verification of the model

        // Over 16 tokens, so padded to the 64 bucket, which is capped at 32
        embedder.embed(TEXT + " " + TEXT, context(), TensorType.fromSpec("tensor<float>(x[16])"));
        assertPaddedToBucket(batching, 32);
    }

    private static OnnxEvaluatorConfig onnxConfig() {
        return new OnnxEvaluatorConfig.Builder().build();
    }

    private static OnnxEvaluatorConfig onnxConfigWithBuckets() {
        var builder = new OnnxEvaluatorConfig.Builder();
        builder.batching.paddingBuckets(List.of(16, 64));
        return builder.build();
    }

    private static Embedder.Context context() {
        return new Embedder.Context("schema.indexing");
    }

    private static void assertPaddedToBucket(BatchingRuntime runtime, int bucket) {
        assertTrue( ! runtime.sequenceLengths.isEmpty(), "The model should have been evaluated");
        for (long length : runtime.sequenceLengths) assertEquals(bucket, length);
    }

    private static void assertClose(Tensor expected, Tensor actual) {
        assertEquals(expected.type(), actual.type());
        assertEquals(expected.size(), actual.size(), "Cells of " + expected + " and " + actual);
        expected.cellIterator().forEachRemaining(cell -> {
            double value = actual.get(cell.getKey());
            assertTrue(Math.abs(cell.getValue() - value) <= TOLERANCE,
                       "Expected " + cell.getValue() + " at " + cell.getKey() + ", got " + value);
        });
    }

    /** A runtime whose evaluators benefit from padding, recording the sequence lengths they get. */
    private static class BatchingRuntime implements OnnxRuntime {

        final List<Long> sequenceLengths = new CopyOnWriteArrayList<>();
        final AtomicInteger paddingQuestions = new AtomicInteger();
        final AtomicInteger evaluations = new AtomicInteger();
        private final OnnxRuntime delegate = OnnxRuntime.testInstance();
        /** Whether to shift the model's outputs by the input length, like a model that attends to the padding */
        private final boolean outputDependsOnLength;

        BatchingRuntime() { this(false); }

        BatchingRuntime(boolean outputDependsOnLength) { this.outputDependsOnLength = outputDependsOnLength; }

        @Override
        public OnnxEvaluator evaluatorOf(String modelPath, OnnxEvaluatorOptions options) {
            var evaluator = delegate.evaluatorOf(modelPath, options);
            return new OnnxEvaluator() {
                @Override
                public Tensor evaluate(Map<String, Tensor> inputs, String output, Duration timeout) {
                    record(inputs);
                    return shifted(evaluator.evaluate(inputs, output, timeout), inputs);
                }
                @Override
                public Map<String, Tensor> evaluate(Map<String, Tensor> inputs, Duration timeout) {
                    record(inputs);
                    var outputs = new java.util.HashMap<String, Tensor>();
                    evaluator.evaluate(inputs, timeout).forEach((name, output) -> outputs.put(name, shifted(output, inputs)));
                    return outputs;
                }
                private Tensor shifted(Tensor output, Map<String, Tensor> inputs) {
                    if ( ! outputDependsOnLength) return output;
                    long length = ((IndexedTensor) inputs.values().iterator().next()).shape()[1];
                    return output.map(x -> x + 0.1 * length);
                }
                @Override
                public boolean shouldPadForBatching() {
                    paddingQuestions.incrementAndGet();
                    return true;
                }
                @Override public Map<String, IdAndType> getInputs() { return evaluator.getInputs(); }
                @Override public Map<String, IdAndType> getOutputs() { return evaluator.getOutputs(); }
                @Override public Map<String, TensorType> getInputInfo() { return evaluator.getInputInfo(); }
                @Override public Map<String, TensorType> getOutputInfo() { return evaluator.getOutputInfo(); }
                @Override public void close() { evaluator.close(); }
            };
        }

        private void record(Map<String, Tensor> inputs) {
            evaluations.incrementAndGet();
            inputs.values().forEach(input -> sequenceLengths.add(((IndexedTensor) input).shape()[1]));
        }
    }

}
