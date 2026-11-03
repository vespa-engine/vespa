// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.modelintegration.evaluator;

import com.yahoo.tensor.IndexedTensor;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests OnnxPadder on its own, over a fake evaluator. That it pads and unpads correctly with a real model is
 * tested in PaddedEmbeddingTest, TritonOnnxEvaluatorTest and TritonPaddedEmbeddingTest.
 *
 * @author ingridhabrekke
 * @author glebashnik
 */
class OnnxPadderTest {

    /** The inputs of an embedder, from which a sample can be made */
    private static final Map<String, TensorType> INPUTS = Map.of(
            "input_ids", TensorType.fromSpec("tensor<float>(d0[],d1[])"),
            "attention_mask", TensorType.fromSpec("tensor<float>(d0[],d1[])"));

    private static final Map<String, TensorType> MIXED_INPUTS = Map.of(
            "input_ids", TensorType.fromSpec("tensor<float>(d0[],d1[])"),
            "attention_mask", TensorType.fromSpec("tensor<float>(d0[],d1[])"),
            "fixed", TensorType.fromSpec("tensor<float>(d0[],d1[3])"));

    /** The scores output is typed like a sequence output, but is fixed at 8 values, like a pooled output exported with a dynamic size */
    private static final Map<String, TensorType> OUTPUTS = Map.of(
            "last_hidden_state", TensorType.fromSpec("tensor<float>(d0[],d1[],d2[16])"),
            "logits", TensorType.fromSpec("tensor<float>(d0[],d1[])"),
            "scores", TensorType.fromSpec("tensor<float>(d0[],d1[])"),
            "pooled", TensorType.fromSpec("tensor<float>(d0[],d1[16])"));

    @Test
    void theEvaluatorIsWrappedInPaddingWhenVerified() {
        var evaluator = new FakeEvaluator(0);
        var padded = OnnxPadder.of(OnnxPadderOptions.of(List.of(16, 64)), evaluator, "model");
        assertTrue(padded instanceof OnnxPadder);
        assertEquals(List.of(16, 64), ((OnnxPadder) padded).buckets());
        assertEquals(evaluator.getInputInfo(), padded.getInputInfo());
        assertEquals(evaluator.getOutputInfo(), padded.getOutputInfo());
        padded.close();
        assertTrue(evaluator.closed, "Closing the padding closes the evaluator");
    }

    @Test
    void bucketsAreSortedAndDistinct() {
        assertEquals(List.of(16, 64), OnnxPadderOptions.of(List.of(64, 16, 16)).buckets());
    }

    @Test
    void bucketsLongerThanTheMaxTokensAreTrimmedToIt() {
        assertEquals(List.of(16, 32), buckets(OnnxPadderOptions.of(List.of(16, 64)).withMaxTokens(32)));
        assertEquals(List.of(16), buckets(OnnxPadderOptions.of(List.of(16, 64, 128)).withMaxTokens(16)));
        assertEquals(List.of(10, 15), buckets(OnnxPadderOptions.of(List.of(10, 20, 30)).withMaxTokens(15)),
                     "Several buckets longer than the max tokens become one at it");
        assertEquals(List.of(16, 64), buckets(OnnxPadderOptions.of(List.of(16, 64)).withMaxTokens(128)),
                     "No bucket is added for the max tokens");
        assertEquals(List.of(16, 64), buckets(OnnxPadderOptions.of(List.of(16, 64)).withMaxTokens(64)));
        assertEquals(List.of(16, 64), buckets(OnnxPadderOptions.of(List.of(16, 64)).withMaxTokens(0)),
                     "Unknown max tokens change nothing");
        assertThrows(IllegalArgumentException.class, () -> OnnxPadderOptions.of(List.of(0, 16)));
    }

    @Test
    void paddingIsDisabledWithoutAnAttentionMask() {
        var evaluator = new FakeEvaluator(0);
        assertSame(evaluator, OnnxPadder.of(OnnxPadderOptions.of(List.of(16)).withAttentionMask("fixed"), evaluator, "model"),
                   "The mask must be a sequence input");
        var withoutMask = new FakeEvaluator(Map.of("input_ids", INPUTS.get("input_ids")), OUTPUTS, 0);
        assertSame(withoutMask, OnnxPadder.of(OnnxPadderOptions.of(List.of(16)), withoutMask, "model"),
                   "Without a mask, the model would attend to the padding");
        assertThrows(IllegalArgumentException.class, () -> OnnxPadderOptions.of(List.of()), "Padding needs buckets");
    }

    @Test
    void inputsArePaddedToTheSmallestBucketThatFits() {
        var evaluator = new FakeEvaluator(0);
        var padded = padded(evaluator, 64, 8);
        for (var length : List.of(3, 8, 9, 65)) {
            evaluator.inputs.clear();
            padded.evaluate(inputs(length));
            assertEquals(length <= 8 ? 8 : length <= 64 ? 64 : length, evaluator.lastInputLength(), "Length " + length);
        }

        evaluator.inputs.clear();
        padded.evaluate(Map.of("input_ids", tensor(1, 3), "attention_mask", tensor(1, 4)));
        assertEquals(3, evaluator.lastInputLength(), "Inputs of different lengths are not padded");
    }

    @Test
    void thePaddedLengthIsTheLengthInputsAreEvaluatedAt() {
        var evaluator = new FakeEvaluator(0);
        var padded = padded(evaluator, 64, 8);
        for (var length : List.of(3, 8, 9, 65)) {
            padded.evaluate(inputs(length));
            assertEquals(evaluator.lastInputLength(), padded.paddedLength(length), "Length " + length);
        }
        assertEquals(5, evaluator.paddedLength(5), "An evaluator which does not pad returns the length unchanged");
    }

    @Test
    void sequenceInputsArePaddedWithZerosAndOtherInputsLeftAlone() {
        var evaluator = new FakeEvaluator(MIXED_INPUTS, OUTPUTS, 0);
        var padded = OnnxPadder.of(OnnxPadderOptions.of(List.of(8)), evaluator, "model");
        assertSame(evaluator, padded, "A sample cannot be made with the fixed input");

        evaluator = new FakeEvaluator(0);
        padded = padded(evaluator, 8);
        var inputs = inputs(3);
        padded.evaluate(inputs);
        assertEquals(Tensor.from("tensor<float>(d0[1],d1[8]):[1, 2, 3, 0, 0, 0, 0, 0]"), evaluator.lastInputs().get("input_ids"));
        assertEquals(Tensor.from("tensor<float>(d0[1],d1[8]):[1, 1, 1, 0, 0, 0, 0, 0]"), evaluator.lastInputs().get("attention_mask"));

        padded.evaluate(inputs(8));
        assertEquals(inputs(8).get("input_ids"), evaluator.lastInputs().get("input_ids"), "Nothing to pad");

        padded.evaluate(Map.of("input_ids", Tensor.from("tensor<float>(d0[2],d1[2]):[[1, 2], [3, 4]]"),
                               "attention_mask", Tensor.from("tensor<float>(d0[2],d1[2]):[[1, 1], [1, 1]]")));
        assertEquals(Tensor.from("tensor<float>(d0[2],d1[8]):[[1, 2, 0, 0, 0, 0, 0, 0], [3, 4, 0, 0, 0, 0, 0, 0]]"),
                     evaluator.lastInputs().get("input_ids"), "Each row is padded");

        var withTokenTypes = new FakeEvaluator(Map.of("input_ids", INPUTS.get("input_ids"),
                                                      "attention_mask", INPUTS.get("attention_mask"),
                                                      "token_type_ids", INPUTS.get("input_ids")), OUTPUTS, 0);
        var inputsWithTokenTypes = new HashMap<>(inputs(3));
        inputsWithTokenTypes.put("token_type_ids", Tensor.from("tensor<float>(d0[1],d1[3]):[0, 0, 1]"));
        padded(withTokenTypes, 8).evaluate(inputsWithTokenTypes);
        assertEquals(Tensor.from("tensor<float>(d0[1],d1[8]):[0, 0, 1, 0, 0, 0, 0, 0]"), withTokenTypes.lastInputs().get("token_type_ids"),
                     "Every sequence input is padded");
    }

    @Test
    void sequenceOutputsAreCutBackToTheRealLength() {
        var evaluator = new FakeEvaluator(0);
        var padded = padded(evaluator, 8);
        var outputs = padded.evaluate(inputs(3));
        assertEquals(8, evaluator.lastInputLength());
        assertEquals(Tensor.from("tensor<float>(d0[1],d1[3]):[1, 1, 1]"), outputs.get("logits"), "Unpadded to the real tokens");
        assertEquals(FakeEvaluator.hidden(3), outputs.get("last_hidden_state"), "Each row keeps the real tokens");
        assertEquals(FakeEvaluator.POOLED, outputs.get("pooled"), "Not a sequence output");
        assertEquals(FakeEvaluator.SCORES, outputs.get("scores"),
                     "An output of the padded length which verification found not to be over the sequence is not unpadded");

        assertEquals(Tensor.from("tensor<float>(d0[1],d1[3]):[1, 1, 1]"), padded.evaluate(inputs(3), "logits"), "Also when evaluating one output");

        var twoRows = padded.evaluate(Map.of("input_ids", Tensor.from("tensor<float>(d0[2],d1[2]):[[1, 2], [3, 4]]"),
                                             "attention_mask", Tensor.from("tensor<float>(d0[2],d1[2]):[[1, 1], [1, 1]]")));
        assertEquals(Tensor.from("tensor<float>(d0[2],d1[2]):[[1, 1], [1, 1]]"), twoRows.get("logits"));

        var unpadded = padded.evaluate(inputs(8));
        assertEquals(8, ((IndexedTensor) unpadded.get("logits")).shape()[1], "Nothing to unpad");
    }

    @Test
    void anOutputPooledByTheRuntimeIsNotASequenceOutput() {
        var evaluator = new FakeEvaluator(0);
        evaluator.runtimePooling = Optional.of(new OnnxPoolingOptions(OnnxPoolingOptions.Strategy.MEAN, "scores", "attention_mask"));
        var padded = padderOf(evaluator, 8);
        assertEquals(evaluator.runtimePooling(), padded.runtimePooling(), "The padding reports the evaluator's pooling");
        var outputs = padded.evaluate(inputs(3));
        assertEquals(FakeEvaluator.SCORES, outputs.get("scores"),
                     "Pooled by the runtime to [batch, embedding], which happens to be the padded length: not unpadded");
        assertEquals(FakeEvaluator.hidden(3), outputs.get("last_hidden_state"), "The sequence outputs still are");
    }

    @Test
    void requestsWithoutTheAttentionMaskAreNotPadded() {
        var evaluator = new FakeEvaluator(0);
        var padded = padded(evaluator, 8);
        padded.evaluate(Map.of("input_ids", tensor(1, 3)));
        assertEquals(3, evaluator.lastInputLength(), "Without a mask the model would attend to the padding");
    }

    @Test
    void sequenceOutputsOfEveryValueTypeAreCutBack() {
        for (var valueType : TensorType.Value.values()) {
            var evaluator = new FakeEvaluator(0);
            evaluator.logitsType = valueType;
            var padded = padded(evaluator, 8);
            var logits = padded.evaluate(inputs(3)).get("logits");
            assertEquals(TensorType.fromSpec("tensor<" + valueType.name().toLowerCase() + ">(d0[1],d1[3])"), logits.type());
            assertEquals(Tensor.from("tensor<" + valueType.name().toLowerCase() + ">(d0[1],d1[3]):[1, 1, 1]"), logits, "Values of type " + valueType);
        }
    }

    @Test
    void paddingIsVerifiedWithASamplePaddedToTheLargestBucket() {
        var evaluator = new FakeEvaluator(0);
        assertTrue(padderOf(evaluator, 8, 64).verify());
        assertEquals(2, evaluator.inputs.size(), "Evaluated unpadded and padded to verify");
        var sample = evaluator.inputs.get(0);
        assertEquals(Set.of("input_ids", "attention_mask"), sample.keySet(), "A sample of the sequence inputs");
        assertEquals(Tensor.from("tensor<float>(d0[1],d1[4]):[1, 1, 1, 1]"), sample.get("attention_mask"));
        assertEquals(Tensor.from("tensor<float>(d0[1],d1[4]):[0, 0, 0, 0]"), sample.get("input_ids"));
        var padded = evaluator.inputs.get(1);
        assertEquals(64, ((IndexedTensor) padded.get("attention_mask")).shape()[1], "Padded to the largest bucket");
        assertEquals(4, padded.get("attention_mask").sum().asDouble(), "The mask is zero for the padding");
    }

    @Test
    void verificationFailsWhenTheSampleCannotBeEvaluated() {
        var evaluator = new FakeEvaluator(0);
        evaluator.failure = new IllegalStateException("Triton is not ready");
        assertFalse(padderOf(evaluator, 8).verify());
        assertSame(evaluator, OnnxPadder.of(OnnxPadderOptions.of(List.of(8)), evaluator, "model"),
                   "A failure to evaluate the sample disables padding");
    }

    @Test
    void verificationPassesWhenTheOutputChangesWithinTheTolerance() {
        assertTrue(padderOf(new FakeEvaluator(OnnxPadder.VERIFICATION_TOLERANCE * 0.9), 8).verify());
    }

    @Test
    void verificationFailsWhenTheOutputChangesBeyondTheTolerance() {
        var evaluator = new FakeEvaluator(OnnxPadder.VERIFICATION_TOLERANCE * 1.1);
        assertFalse(padderOf(evaluator, 8).verify());
        assertSame(evaluator, OnnxPadder.of(OnnxPadderOptions.of(List.of(8)), evaluator, "model"), "Padding is then not used");
    }

    @Test
    void aFewCellsRoundedDifferentlyAreTolerated() {
        // One large cell differs by 1% of itself, as fp16 arithmetic rounds it at another batch shape:
        // a change of 3.7% relative to the mean cell, but of 1% relative to the output
        assertTrue(padderOf(new FakeEvaluator(new double[] { 20, 0.5, 0.5, 0.5 }, new double[] { 20.2, 0.5, 0.5, 0.5 }), 8).verify());
        // Every cell changing by 5% is a change of 5%, whatever their sizes
        assertFalse(padderOf(new FakeEvaluator(new double[] { 20, 0.5, 0.5, 0.5 }, new double[] { 21, 0.525, 0.525, 0.525 }), 8).verify());
    }

    @Test
    void verificationFailsWhenPaddingMakesTheOutputNaN() {
        assertFalse(padderOf(new FakeEvaluator(Double.NaN), 8).verify());
    }

    @Test
    void verificationFailsWhenThePaddedOutputHasAnotherShape() {
        var evaluator = new FakeEvaluator(0);
        evaluator.paddedLogitsLength = 5; // Like a model whose output length is not the sequence length
        assertFalse(padderOf(evaluator, 8).verify());

        var sequenceFirst = new FakeEvaluator(0);
        sequenceFirst.sequenceFirstOutput = true; // Like a model outputting [sequence, batch, hidden], which cannot be unpadded
        assertFalse(padderOf(sequenceFirst, 8).verify());
    }

    @Test
    void paddingIsDisabledWhenNoBucketIsLongerThanTheSample() {
        var evaluator = new FakeEvaluator(0);
        assertTrue(OnnxPadder.padderOf(OnnxPadderOptions.of(List.of(1)), evaluator, "model").isEmpty(), "Nothing to compare");
        assertSame(evaluator, OnnxPadder.of(OnnxPadderOptions.of(List.of(1)), evaluator, "model"));
        assertEquals(0, evaluator.inputs.size());
    }

    @Test
    void paddingIsWorthwhileWhenBatchingOnAGpu() {
        var options = new OnnxEvaluatorOptions.Builder(16);
        assertTrue(OnnxPadder.isBatchingOnGpu(options.setBatchingMaxSize(8).setGpuDevice(0).build()));
        assertFalse(OnnxPadder.isBatchingOnGpu(options.setBatchingMaxSize(1).setGpuDevice(0).build()), "Needs batching");
        assertFalse(OnnxPadder.isBatchingOnGpu(options.setBatchingMaxSize(8).setGpuDevice(-1).build()), "Needs a GPU");
    }

    @Test
    void dynamicallyQuantizedModelsAreDetected() {
        assertTrue(OnnxPadder.isDynamicallyQuantized("src/test/models/onnx/transformer/embedding_model.onnx"));
        assertFalse(OnnxPadder.isDynamicallyQuantized("src/test/models/onnx/transformer/dummy_masked_attention_embedder.onnx"));
        assertFalse(OnnxPadder.isDynamicallyQuantized("not-a-local-file"));
    }

    private static List<Integer> buckets(OnnxPadderOptions options) {
        return OnnxPadder.padderOf(options, new FakeEvaluator(0), "model").orElseThrow().buckets();
    }

    /** Returns the padding of the evaluator to the buckets, not verified */
    private static OnnxPadder padderOf(FakeEvaluator evaluator, Integer... buckets) {
        return OnnxPadder.padderOf(OnnxPadderOptions.of(List.of(buckets)), evaluator, "model").orElseThrow();
    }

    /** Returns the evaluator wrapped in verified padding to the buckets */
    private static OnnxEvaluator padded(FakeEvaluator evaluator, Integer... buckets) {
        var padded = OnnxPadder.of(OnnxPadderOptions.of(List.of(buckets)), evaluator, "model");
        assertTrue(padded instanceof OnnxPadder);
        evaluator.inputs.clear();
        return padded;
    }

    /** Inputs of the given length, with ids 1 to length and a mask of ones. */
    private static Map<String, Tensor> inputs(int length) {
        var mask = Tensor.Builder.of(TensorType.fromSpec("tensor<float>(d0[1],d1[" + length + "])"));
        for (int i = 0; i < length; i++) mask.cell(1, 0, i);
        return Map.of("input_ids", tensor(1, length), "attention_mask", mask.build());
    }

    /** A [rows, length] tensor with cells 1, 2, ... */
    private static IndexedTensor tensor(int rows, int length) {
        var builder = Tensor.Builder.of(TensorType.fromSpec("tensor<float>(d0[" + rows + "],d1[" + length + "])"));
        for (int row = 0; row < rows; row++)
            for (int i = 0; i < length; i++)
                builder.cell(row * length + i + 1, row, i);
        return (IndexedTensor) builder.build();
    }

    /**
     * A model outputting 'logits' with a value per token over the whole input, 'last_hidden_state' with a vector
     * per token, and a fixed size 'pooled' output. The given values for the real tokens, 0 for tokens with a zero
     * mask, and the padded values for the real tokens when the mask has zeros, like a model that attends to the
     * padding if they differ.
     */
    private static class FakeEvaluator implements OnnxEvaluator {

        static final Tensor POOLED = Tensor.from("tensor<float>(d0[1],d1[16]):[1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16]");
        static final Tensor SCORES = Tensor.from("tensor<float>(d0[1],d1[8]):[1, 2, 3, 4, 5, 6, 7, 8]");

        private final Map<String, TensorType> inputTypes;
        private final Map<String, TensorType> outputTypes;
        private final double[] values;
        private final double[] paddedValues;
        final List<Map<String, Tensor>> inputs = new ArrayList<>();
        RuntimeException failure = null;
        /** The length of the logits of a padded input, if not its length */
        int paddedLogitsLength = -1;
        TensorType.Value logitsType = TensorType.Value.FLOAT;
        /** Outputs last_hidden_state as [sequence, batch, 16] instead of [batch, sequence, 16] */
        boolean sequenceFirstOutput = false;
        boolean closed = false;
        Optional<OnnxPoolingOptions> runtimePooling = Optional.empty();

        /** Outputs 1 for each real token, multiplied by 1 + change when padded: a relative difference of change. */
        FakeEvaluator(double change) {
            this(new double[] { 1, 1, 1, 1 }, new double[] { 1 + change, 1 + change, 1 + change, 1 + change });
        }

        FakeEvaluator(double[] values, double[] paddedValues) {
            this(INPUTS, OUTPUTS, values, paddedValues);
        }

        FakeEvaluator(Map<String, TensorType> inputTypes, Map<String, TensorType> outputTypes, double change) {
            this(inputTypes, outputTypes, new double[] { 1, 1, 1, 1 }, new double[] { 1 + change, 1 + change, 1 + change, 1 + change });
        }

        FakeEvaluator(Map<String, TensorType> inputTypes, Map<String, TensorType> outputTypes, double[] values, double[] paddedValues) {
            this.inputTypes = inputTypes;
            this.outputTypes = outputTypes;
            this.values = values;
            this.paddedValues = paddedValues;
        }

        Map<String, Tensor> lastInputs() { return inputs.get(inputs.size() - 1); }

        int lastInputLength() { return (int) ((IndexedTensor) lastInputs().get("input_ids")).shape()[1]; }

        /** A [1, tokens, 16] tensor with cell token * 100 + dimension */
        static Tensor hidden(int tokens) {
            var builder = Tensor.Builder.of(TensorType.fromSpec("tensor<float>(d0[1],d1[" + tokens + "],d2[16])"));
            for (int token = 0; token < tokens; token++)
                for (int d = 0; d < 16; d++)
                    builder.cell(token * 100 + d, 0, token, d);
            return builder.build();
        }

        @Override
        public Tensor evaluate(Map<String, Tensor> inputs, String output, Duration timeout) {
            return evaluate(inputs, timeout).get(output);
        }

        @Override
        public Map<String, Tensor> evaluate(Map<String, Tensor> inputs, Duration timeout) {
            this.inputs.add(inputs);
            if (failure != null) throw failure;
            var mask = inputs.containsKey("attention_mask") ? (IndexedTensor) inputs.get("attention_mask")
                                                            : ones(((IndexedTensor) inputs.get("input_ids")).type());
            int rows = (int) mask.shape()[0];
            int length = (int) mask.shape()[1];
            boolean padded = mask.sum().asDouble() < rows * length;
            int outputLength = padded && paddedLogitsLength > 0 ? paddedLogitsLength : length;
            var tokenValues = padded ? paddedValues : values;
            var logits = Tensor.Builder.of(TensorType.fromSpec("tensor<" + logitsType.name().toLowerCase() + ">(d0[" + rows + "],d1[" + outputLength + "])"));
            for (int row = 0; row < rows; row++)
                for (int i = 0; i < outputLength; i++)
                    logits.cell(i >= length || mask.get(row, i) == 0 ? 0 : i < tokenValues.length ? tokenValues[i] : 1, row, i);
            var outputs = new HashMap<String, Tensor>();
            outputs.put("logits", logits.build());
            if (rows == 1) outputs.put("last_hidden_state", sequenceFirstOutput ? sequenceFirst(length) : hidden(length));
            outputs.put("pooled", POOLED);
            outputs.put("scores", SCORES);
            return outputs;
        }

        /** A [tokens, 1, 16] tensor with cell token * 100 + dimension */
        static Tensor sequenceFirst(int tokens) {
            var builder = Tensor.Builder.of(TensorType.fromSpec("tensor<float>(d0[" + tokens + "],d1[1],d2[16])"));
            for (int token = 0; token < tokens; token++)
                for (int d = 0; d < 16; d++)
                    builder.cell(token * 100 + d, token, 0, d);
            return builder.build();
        }

        private static IndexedTensor ones(TensorType type) {
            var builder = (IndexedTensor.BoundBuilder) Tensor.Builder.of(type, com.yahoo.tensor.DimensionSizes.of(type));
            for (long i = 0; i < builder.type().dimensions().get(1).size().orElseThrow(); i++) builder.cellByDirectIndex(i, 1);
            return builder.build();
        }

        @Override public Map<String, IdAndType> getInputs() { throw new UnsupportedOperationException(); }
        @Override public Map<String, IdAndType> getOutputs() { throw new UnsupportedOperationException(); }
        @Override public Map<String, TensorType> getInputInfo() { return inputTypes; }
        @Override public Map<String, TensorType> getOutputInfo() { return outputTypes; }
        @Override public Optional<OnnxPoolingOptions> runtimePooling() { return runtimePooling; }
        @Override public void close() { closed = true; }

    }

}
