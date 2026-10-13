// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.embedding;

import ai.vespa.modelintegration.evaluator.OnnxEvaluator;
import ai.vespa.modelintegration.evaluator.config.OnnxEvaluatorConfig;
import com.yahoo.tensor.IndexedTensor;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests SequencePadder on its own, with a fake evaluator. That embedders pad correctly is tested in PaddedEmbeddingTest.
 *
 * @author ingrid
 */
class SequencePadderTest {

    @Test
    void configuredBucketsAreUsed() {
        var padder = SequencePadder.of(List.of(64, 8));
        assertEquals(8, padder.paddedLength(3));
        assertEquals(8, padder.paddedLength(8));
        assertEquals(64, padder.paddedLength(9));
        assertEquals(65, padder.paddedLength(65));
        assertEquals(List.of(1L, 2L, 0L, 0L, 0L, 0L, 0L, 0L), padder.pad(List.of(1L, 2L), 0L));
    }

    @Test
    void bucketsAreSortedAndDeduplicated() {
        assertEquals("padding-buckets [8, 64]", SequencePadder.of(List.of(64, 8, 8)).toString());
    }

    @Test
    void nonPositiveBucketsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> SequencePadder.of(List.of(0, 16)));
        assertThrows(IllegalArgumentException.class, () -> SequencePadder.of(List.of(-8)));
    }

    @Test
    void paddingIsDisabledWithoutBuckets() {
        assertEquals(List.of(1L, 2L), SequencePadder.disabled().pad(List.of(1L, 2L), 0L));
        assertEquals(3, SequencePadder.disabled().paddedLength(3));
        assertEquals(3, SequencePadder.of(List.of()).paddedLength(3));
        assertEquals("no padding-buckets", SequencePadder.disabled().toString());
    }

    @Test
    void valuesThatNeedNoPaddingAreReturnedAsIs() {
        var padder = SequencePadder.of(List.of(4));
        var values = List.of(1, 2, 3, 4);
        assertSame(values, padder.pad(values, 0));
        assertSame(values, SequencePadder.disabled().pad(values, 0));
    }

    @Test
    void anyValueTypeCanBePadded() {
        assertEquals(List.of(7, 0, 0, 0), SequencePadder.of(List.of(4)).pad(List.of(7), 0));
    }

    @Test
    void padderIsDisabledWithoutConfiguredBuckets() {
        var evaluator = new FakeEvaluator(0);
        assertTrue(SequencePadder.of(evaluator, config(), 512, "model").isDisabled());
        assertEquals(0, evaluator.paddingQuestions, "The evaluator should not be asked without buckets");
    }

    @Test
    void padderIsDisabledWhenTheEvaluatorShouldNotPad() {
        var evaluator = new FakeEvaluator(0);
        evaluator.shouldPad = false;
        assertTrue(SequencePadder.of(evaluator, config(16, 64), 512, "model").isDisabled());
        assertEquals(1, evaluator.paddingQuestions);
    }

    @Test
    void padderUsesTheConfiguredBucketsWhenTheEvaluatorShouldPad() {
        var padder = SequencePadder.of(new FakeEvaluator(0), config(64, 16), 512, "model");
        assertEquals("padding-buckets [16, 64]", padder.toString());
    }

    @Test
    void bucketsLargerThanTheModelMaxTokensAreCapped() {
        assertEquals("padding-buckets [16, 32]", SequencePadder.of(new FakeEvaluator(0), config(16, 64), 32, "model").toString());
        assertEquals("padding-buckets [16]", SequencePadder.of(new FakeEvaluator(0), config(16, 64, 128), 16, "model").toString());
    }

    @Test
    void bucketsLargerThanTheModelMaxTokensAreIgnoredWhenNotPadding() {
        var evaluator = new FakeEvaluator(0);
        evaluator.shouldPad = false;
        assertTrue(SequencePadder.of(evaluator, config(16, 1024), 32, "model").isDisabled());
    }

    @Test
    void paddingIsKeptWhenTheOutputIsUnchanged() {
        var evaluator = new FakeEvaluator(0);
        var padder = SequencePadder.of(List.of(16));
        assertSame(padder, padder.verifiedWith(evaluator, SequencePadderTest::sampleInputs, 5, "model"));
        assertEquals(List.of(5L, 16L), evaluator.sequenceLengths, "Should evaluate the sample unpadded and padded");
    }

    @Test
    void paddingIsKeptWhenTheOutputChangesWithinTheTolerance() {
        var padder = SequencePadder.of(List.of(16));
        var evaluator = new FakeEvaluator(SequencePadder.VERIFICATION_TOLERANCE * 0.9);
        assertSame(padder, padder.verifiedWith(evaluator, SequencePadderTest::sampleInputs, 5, "model"));
    }

    @Test
    void paddingIsDisabledWhenTheOutputChangesBeyondTheTolerance() {
        var padder = SequencePadder.of(List.of(16));
        var evaluator = new FakeEvaluator(SequencePadder.VERIFICATION_TOLERANCE * 1.1);
        assertTrue(padder.verifiedWith(evaluator, SequencePadderTest::sampleInputs, 5, "model").isDisabled());
    }

    @Test
    void outlierValuesDoNotHideChangesToTypicalValues() {
        // The typical values change by 20%. Relative to the largest value, that would be a change of only 0.5%
        var padder = SequencePadder.of(List.of(16));
        var evaluator = new FakeEvaluator(new double[] { 20, 0.5, 0.5, 0.5, 0.5 },
                                          new double[] { 20, 0.6, 0.6, 0.6, 0.6 });
        assertTrue(padder.verifiedWith(evaluator, SequencePadderTest::sampleInputs, 5, "model").isDisabled());
    }

    @Test
    void paddingIsDisabledWhenItMakesTheOutputNaN() {
        var padder = SequencePadder.of(List.of(16));
        var evaluator = new FakeEvaluator(Double.NaN);
        assertTrue(padder.verifiedWith(evaluator, SequencePadderTest::sampleInputs, 5, "model").isDisabled());
    }

    @Test
    void disabledPaddingIsNotVerified() {
        var evaluator = new FakeEvaluator(1);
        var padder = SequencePadder.disabled();
        assertSame(padder, padder.verifiedWith(evaluator, SequencePadderTest::sampleInputs, 5, "model"));
        assertEquals(List.of(), evaluator.sequenceLengths);
    }

    @Test
    void paddingIsDisabledWhenNoBucketIsLongerThanTheSample() {
        for (var buckets : List.of(List.of(4), List.of(4, 5))) {
            var evaluator = new FakeEvaluator(0);
            var padder = SequencePadder.of(buckets);
            assertTrue(padder.verifiedWith(evaluator, SequencePadderTest::sampleInputs, 5, "model").isDisabled(), buckets.toString());
            assertEquals(List.of(), evaluator.sequenceLengths, "Nothing to compare, so the model should not be evaluated");
        }
    }

    @Test
    void paddingIsVerifiedAtTheLargestBucket() {
        var evaluator = new FakeEvaluator(0);
        var padder = SequencePadder.of(List.of(5, 8, 16));
        assertSame(padder, padder.verifiedWith(evaluator, SequencePadderTest::sampleInputs, 5, "model"));
        assertEquals(List.of(5L, 16L), evaluator.sequenceLengths, "Should evaluate the sample unpadded and padded to 16");
    }

    @Test
    void paddingIsDisabledWhenTheVerificationFails() {
        var evaluator = new FakeEvaluator(0);
        evaluator.failure = new IllegalStateException("Triton is not ready");
        var padder = SequencePadder.of(List.of(16));
        assertTrue(padder.verifiedWith(evaluator, SequencePadderTest::sampleInputs, 5, "model").isDisabled());
    }

    private static OnnxEvaluatorConfig config(Integer... buckets) {
        var builder = new OnnxEvaluatorConfig.Builder();
        builder.batching.paddingBuckets(List.of(buckets));
        return builder.build();
    }

    /** The inputs of a sample of 5 tokens with ids 1 to 5, padded with 0 by the given padder. */
    private static Map<String, Tensor> sampleInputs(SequencePadder padder) {
        var ids = padder.pad(List.of(1L, 2L, 3L, 4L, 5L), 0L);
        var builder = Tensor.Builder.of(TensorType.fromSpec("tensor<float>(d0[1],d1[" + ids.size() + "])"));
        for (int i = 0; i < ids.size(); i++) builder.cell(ids.get(i), 0, i);
        return Map.of("ids", builder.build());
    }

    /**
     * Outputs a value for each token: the given values for the real tokens when the input is not padded,
     * and the given padded values when it is, like a model that attends to the padding if they differ.
     */
    private static class FakeEvaluator implements OnnxEvaluator {

        private final double[] values;
        private final double[] paddedValues;
        final List<Long> sequenceLengths = new ArrayList<>();
        boolean shouldPad = true;
        int paddingQuestions = 0;
        RuntimeException failure = null;

        /** Outputs 1 for each real token, multiplied by 1 + change when padded, a relative change of change. */
        FakeEvaluator(double change) {
            this(new double[] { 1, 1, 1, 1, 1 }, new double[] { 1 + change, 1 + change, 1 + change, 1 + change, 1 + change });
        }

        FakeEvaluator(double[] values, double[] paddedValues) {
            this.values = values;
            this.paddedValues = paddedValues;
        }

        @Override
        public Map<String, Tensor> evaluate(Map<String, Tensor> inputs, Duration timeout) {
            if (failure != null) throw failure;
            var ids = (IndexedTensor) inputs.get("ids");
            long length = ids.shape()[1];
            sequenceLengths.add(length);
            boolean padded = ids.get(0, length - 1) == 0;
            var output = Tensor.Builder.of(ids.type());
            for (int i = 0; i < length; i++) {
                boolean realToken = ids.get(0, i) != 0;
                output.cell(realToken ? (padded ? paddedValues[i] : values[i]) : 0, 0, i);
            }
            return Map.of("output", output.build());
        }

        @Override
        public Tensor evaluate(Map<String, Tensor> inputs, String output, Duration timeout) {
            return evaluate(inputs, timeout).get(output);
        }

        @Override
        public boolean shouldPadForBatching() {
            paddingQuestions++;
            return shouldPad;
        }

        @Override public Map<String, IdAndType> getInputs() { return Map.of(); }
        @Override public Map<String, IdAndType> getOutputs() { return Map.of(); }
        @Override public Map<String, TensorType> getInputInfo() { return Map.of(); }
        @Override public Map<String, TensorType> getOutputInfo() { return Map.of(); }
        @Override public void close() { }

    }

}
