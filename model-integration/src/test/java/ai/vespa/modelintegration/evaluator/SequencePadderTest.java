// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.modelintegration.evaluator;

import ai.vespa.modelintegration.evaluator.SequencePadder.Request;
import com.yahoo.tensor.IndexedTensor;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests SequencePadder on its own, with a fake evaluation. That the Triton evaluator pads and unpads correctly
 * with a real model is tested in TritonOnnxEvaluatorTest and TritonPaddedEmbeddingTest.
 *
 * @author ingridhabrekke
 * @author glebashnik
 */
class SequencePadderTest {

    private static final Map<String, TensorType> INPUTS = Map.of(
            "input_ids", TensorType.fromSpec("tensor<float>(d0[],d1[])"),
            "attention_mask", TensorType.fromSpec("tensor<float>(d0[],d1[])"),
            "fixed", TensorType.fromSpec("tensor<float>(d0[],d1[3])"),
            "rank3", TensorType.fromSpec("tensor<float>(d0[],d1[],d2[])"));

    /** The inputs of an embedder, from which a sample can be made */
    private static final Map<String, TensorType> SEQUENCE_INPUTS = Map.of(
            "input_ids", TensorType.fromSpec("tensor<float>(d0[],d1[])"),
            "attention_mask", TensorType.fromSpec("tensor<float>(d0[],d1[])"));

    private static final Map<String, TensorType> OUTPUTS = Map.of(
            "last_hidden_state", TensorType.fromSpec("tensor<float>(d0[],d1[],d2[16])"),
            "logits", TensorType.fromSpec("tensor<float>(d0[],d1[])"),
            "pooled", TensorType.fromSpec("tensor<float>(d0[],d1[16])"));

    @Test
    void sequenceInputsAndOutputsAreResolvedFromTheTypes() {
        var padder = SequencePadder.of(SequencePadderOptions.of(List.of(16, 64)), INPUTS, OUTPUTS, "model");
        assertEquals(Set.of("input_ids", "attention_mask"), padder.sequenceInputs(), "The [batch, sequence] inputs");
        assertEquals(Set.of("last_hidden_state", "logits"), padder.sequenceOutputs(), "The outputs with an unbound second dimension");
        assertEquals(List.of(16, 64), padder.buckets());
        assertEquals(SequencePadder.State.UNVERIFIED, padder.state());
    }

    @Test
    void bucketsAreSortedDeduplicatedAndCapped() {
        assertEquals(List.of(16, 64), SequencePadder.of(SequencePadderOptions.of(List.of(64, 16, 16)), INPUTS, OUTPUTS, "model").buckets());
        assertEquals(List.of(16, 32), SequencePadder.of(SequencePadderOptions.of(List.of(16, 64)).withMaxLength(32), INPUTS, OUTPUTS, "model").buckets());
        assertEquals(List.of(16), SequencePadder.of(SequencePadderOptions.of(List.of(16, 64, 128)).withMaxLength(16), INPUTS, OUTPUTS, "model").buckets());
        assertEquals(List.of(16, 64), SequencePadder.of(SequencePadderOptions.of(List.of(16, 64)).withMaxLength(0), INPUTS, OUTPUTS, "model").buckets(),
                     "An unknown max length does not cap");
        assertThrows(IllegalArgumentException.class, () -> SequencePadderOptions.of(List.of(0, 16)));
    }

    @Test
    void paddingIsDisabledWithoutAnAttentionMask() {
        assertEquals(SequencePadder.State.DISABLED, SequencePadder.of(SequencePadderOptions.of(List.of(16)).withAttentionMask("fixed"), INPUTS, OUTPUTS, "model").state(),
                   "The mask must be a sequence input");
        assertEquals(SequencePadder.State.DISABLED, SequencePadder.of(SequencePadderOptions.of(List.of(16)), Map.of("input_ids", INPUTS.get("input_ids")), OUTPUTS, "model").state(),
                   "Without a mask, the model would attend to the padding");
        assertThrows(IllegalArgumentException.class, () -> SequencePadderOptions.of(List.of()), "Padding needs buckets");
    }

    @Test
    void requestsArePaddedToTheSmallestBucketThatFits() {
        var padder = SequencePadder.of(SequencePadderOptions.of(List.of(64, 8)), INPUTS, OUTPUTS, "model");
        assertEquals(new Request(3, 8), padder.requestFor(inputs(3)));
        assertEquals(new Request(8, 8), padder.requestFor(inputs(8)));
        assertEquals(new Request(9, 64), padder.requestFor(inputs(9)));
        assertEquals(new Request(65, 65), padder.requestFor(inputs(65)));
        assertFalse(padder.requestFor(inputs(8)).isPadded());
        assertEquals(Request.unpadded(0), padder.requestFor(Map.<String, Tensor>of("fixed", tensor(1, 3))), "No sequence inputs");
        assertEquals(Request.unpadded(0), padder.requestFor(Map.<String, Tensor>of("input_ids", tensor(1, 3), "attention_mask", tensor(1, 4))),
                     "Inputs of different lengths are not padded");
    }

    @Test
    void sequenceInputsArePaddedWithZeros() {
        var padder = SequencePadder.of(SequencePadderOptions.of(List.of(8)), INPUTS, OUTPUTS, "model");
        var inputs = Map.<String, Tensor>of("input_ids", tensor(1, 3), "fixed", tensor(1, 3));
        var request = padder.requestFor(inputs);
        var padded = padder.pad(inputs, request);
        assertEquals(Tensor.from("tensor<float>(d0[1],d1[8]):[1, 2, 3, 0, 0, 0, 0, 0]"), padded.get("input_ids"));
        assertSame(inputs.get("fixed"), padded.get("fixed"));
        assertSame(inputs, padder.pad(inputs, new Request(3, 3)), "Nothing to pad");

        var twoRows = Tensor.from("tensor<float>(d0[2],d1[2]):[[1, 2], [3, 4]]");
        assertEquals(Tensor.from("tensor<float>(d0[2],d1[3]):[[1, 2, 0], [3, 4, 0]]"),
                     padder.pad(Map.<String, Tensor>of("input_ids", twoRows), new Request(2, 3)).get("input_ids"));
    }

    @Test
    void sequenceOutputsAreSlicedToTheRealLength() {
        var padder = padder(8);
        var request = new Request(3, 8);
        var slice = padder.sliceFor("last_hidden_state", new long[] {1, 8, 16}, request);
        assertEquals(new SequencePadder.Slice(1, 3 * 16, 8 * 16), slice);
        assertEquals(TensorType.fromSpec("tensor<float>(d0[1],d1[3],d2[16])"),
                     padder.slicedType(TensorType.fromSpec("tensor<float>(d0[1],d1[8],d2[16])"), request));

        assertEquals(new SequencePadder.Slice(2, 3, 8), padder.sliceFor("logits", new long[] {2, 8}, request));
        assertNull(padder.sliceFor("pooled", new long[] {1, 16}, request), "Not a sequence output");
        assertNull(padder.sliceFor("last_hidden_state", new long[] {1, 8, 16}, new Request(8, 8)), "Not padded");
        assertNull(padder.sliceFor("last_hidden_state", new long[] {1, 5, 16}, request), "Not the padded length");
    }

    @Test
    void paddingIsVerifiedAtConstructionWithASamplePaddedToTheLargestBucket() {
        var padder = padder(8, 64);
        var evaluation = new FakeEvaluation(0);
        padder.verify(evaluation);
        assertEquals(List.of(Request.unpadded(4), new Request(4, 64)), evaluation.requests, "Evaluated unpadded and padded to verify");
        assertEquals(SequencePadder.State.ENABLED, padder.state());
        var sample = evaluation.inputs.get(0);
        assertEquals(Set.of("input_ids", "attention_mask"), sample.keySet(), "A sample of the sequence inputs");
        assertEquals(Tensor.from("tensor<float>(d0[1],d1[4]):[1, 1, 1, 1]"), sample.get("attention_mask"));
        assertEquals(Tensor.from("tensor<float>(d0[1],d1[4]):[0, 0, 0, 0]"), sample.get("input_ids"));

        evaluation.requests.clear();
        padder.evaluate(inputs(5), evaluation);
        assertEquals(List.of(new Request(5, 8)), evaluation.requests, "Padded without verification once verified");
    }

    @Test
    void sequenceOutputsAreRefinedToThoseWithTheSequenceLengthAtVerification() {
        var padder = padder(8);
        assertEquals(Set.of("last_hidden_state", "logits"), padder.sequenceOutputs(), "From the types");
        padder.verify(new FakeEvaluation(0));
        assertEquals(Set.of("logits"), padder.sequenceOutputs(), "The fake only outputs logits over the sequence");
        assertNull(padder.sliceFor("last_hidden_state", new long[] {1, 8, 16}, new Request(3, 8)),
                   "An output found not to be over the sequence is not sliced, even at the padded length");
    }

    @Test
    void verificationWaitsForTheFirstPaddedRequestWhenASampleCannotBeMade() {
        var inputs = new java.util.HashMap<>(INPUTS); // The fixed and rank 3 inputs cannot be made up
        var padder = SequencePadder.of(SequencePadderOptions.of(List.of(8)), inputs, OUTPUTS, "model");
        var evaluation = new FakeEvaluation(0);
        padder.verify(evaluation);
        assertEquals(List.of(), evaluation.requests);
        assertEquals(SequencePadder.State.UNVERIFIED, padder.state());

        padder.evaluate(inputs(3), evaluation);
        assertEquals(List.of(Request.unpadded(3), new Request(3, 8)), evaluation.requests, "Verified with the request");
        assertEquals(SequencePadder.State.ENABLED, padder.state());
    }

    @Test
    void requestsThatNeedNoPaddingAreNotVerified() {
        var padder = padder(8);
        var evaluation = new FakeEvaluation(0);
        padder.evaluate(inputs(8), evaluation);
        padder.evaluate(inputs(9), evaluation);
        assertEquals(List.of(new Request(8, 8), new Request(9, 9)), evaluation.requests);
        assertEquals(SequencePadder.State.UNVERIFIED, padder.state());
    }

    @Test
    void paddingIsKeptWhenTheOutputChangesWithinTheTolerance() {
        var padder = padder(8);
        padder.verify(new FakeEvaluation(SequencePadder.VERIFICATION_TOLERANCE * 0.9));
        assertEquals(SequencePadder.State.ENABLED, padder.state());
    }

    @Test
    void paddingIsDisabledWhenTheOutputChangesBeyondTheTolerance() {
        var padder = padder(8);
        var evaluation = new FakeEvaluation(SequencePadder.VERIFICATION_TOLERANCE * 1.1);
        padder.verify(evaluation);
        assertEquals(SequencePadder.State.DISABLED, padder.state());

        evaluation.requests.clear();
        padder.evaluate(inputs(3), evaluation);
        assertEquals(List.of(Request.unpadded(3)), evaluation.requests, "Not padded once disabled");
    }

    @Test
    void outlierValuesDoNotHideChangesToTypicalValues() {
        // The typical values change by 20%. Relative to the largest value, that would be a change of only 0.5%
        var padder = padder(8);
        padder.verify(new FakeEvaluation(new double[] { 20, 0.5, 0.5, 0.5 }, new double[] { 20, 0.6, 0.6, 0.6 }));
        assertEquals(SequencePadder.State.DISABLED, padder.state());
    }

    @Test
    void paddingIsDisabledWhenItMakesTheOutputNaN() {
        var padder = padder(8);
        padder.verify(new FakeEvaluation(Double.NaN));
        assertEquals(SequencePadder.State.DISABLED, padder.state());
    }

    @Test
    void paddingIsDisabledWhenThePaddedOutputHasAnotherShape() {
        var padder = padder(8);
        var evaluation = new FakeEvaluation(0);
        evaluation.cutOutputs = false; // Like an evaluator that fails to cut a sequence output
        padder.verify(evaluation);
        assertEquals(SequencePadder.State.DISABLED, padder.state());
    }

    @Test
    void verificationIsRetriedWhenThePaddedEvaluationFailsUntilItHasFailedAFewTimes() {
        var padder = padder(8);
        var evaluation = new FakeEvaluation(0);
        evaluation.failure = new IllegalStateException("Triton is not ready");
        padder.verify(evaluation);
        assertEquals(SequencePadder.State.UNVERIFIED, padder.state(), "A failure to evaluate is not a verdict");

        var outputs = padder.evaluate(inputs(3), evaluation);
        assertEquals(SequencePadder.State.UNVERIFIED, padder.state());
        assertEquals(3, ((IndexedTensor) outputs.get("logits")).shape()[1], "The unpadded output is returned");
        padder.evaluate(inputs(3), evaluation);
        assertEquals(SequencePadder.State.DISABLED, padder.state(), "Disabled after the third failure");

        evaluation.failure = null;
        evaluation.requests.clear();
        padder.evaluate(inputs(3), evaluation);
        assertEquals(List.of(Request.unpadded(3)), evaluation.requests);
    }

    @Test
    void paddingIsDisabledWhenNoBucketIsLongerThanTheSample() {
        var padder = padder(1);
        var evaluation = new FakeEvaluation(0);
        padder.verify(evaluation);
        assertEquals(List.of(), evaluation.requests, "Nothing to compare");
        assertEquals(SequencePadder.State.DISABLED, padder.state());
    }

    @Test
    void paddingIsWorthwhileWhenBatchingOnAGpu() {
        var options = new OnnxEvaluatorOptions.Builder(16);
        assertTrue(SequencePadder.isBatchingOnGpu(options.setBatchingMaxSize(8).setGpuDevice(0).build()));
        assertFalse(SequencePadder.isBatchingOnGpu(options.setBatchingMaxSize(1).setGpuDevice(0).build()), "Needs batching");
        assertFalse(SequencePadder.isBatchingOnGpu(options.setBatchingMaxSize(8).setGpuDevice(-1).build()), "Needs a GPU");
    }

    @Test
    void verificationWaitsForTheFirstPaddedRequestWhenTheSampleCannotBeEvaluated() {
        var padder = padder(8);
        var evaluation = new FakeEvaluation(0);
        evaluation.failUnpadded = true; // Like a model whose first inference fails or times out
        padder.verify(evaluation);
        assertEquals(SequencePadder.State.UNVERIFIED, padder.state(), "A failure to evaluate the sample is not a verdict");

        evaluation.failUnpadded = false;
        evaluation.requests.clear();
        padder.evaluate(inputs(3), evaluation);
        assertEquals(List.of(Request.unpadded(3), new Request(3, 8)), evaluation.requests, "Verified with the request instead");
        assertEquals(SequencePadder.State.ENABLED, padder.state());
    }

    @Test
    void requestsWaitingForVerificationEvaluateConcurrentlyOnceItIsDecided() throws Exception {
        var inputs = new java.util.HashMap<>(INPUTS); // Not all sequences, so verification waits for a request
        var padder = SequencePadder.of(SequencePadderOptions.of(List.of(8)), inputs, OUTPUTS, "model");
        var evaluation = new FakeEvaluation(0);
        evaluation.holdVerification = new java.util.concurrent.CountDownLatch(1);
        evaluation.evaluationDuration = java.time.Duration.ofMillis(100);
        var executor = java.util.concurrent.Executors.newFixedThreadPool(4);
        try {
            var requests = new ArrayList<java.util.concurrent.Future<Map<String, Tensor>>>();
            for (int i = 0; i < 4; i++) requests.add(executor.submit(() -> padder.evaluate(inputs(3), evaluation)));
            while (evaluation.requests.isEmpty()) Thread.sleep(1); // The first request is verifying, the others wait
            evaluation.holdVerification.countDown();
            for (var request : requests) request.get();
            assertEquals(SequencePadder.State.ENABLED, padder.state());
            assertTrue(evaluation.maxConcurrent.get() > 1, "The waiting requests should evaluate concurrently, not under the lock");
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void dynamicallyQuantizedModelsAreDetected() {
        assertTrue(SequencePadder.isDynamicallyQuantized("src/test/models/onnx/transformer/embedding_model.onnx"));
        assertFalse(SequencePadder.isDynamicallyQuantized("src/test/models/onnx/transformer/dummy_masked_attention_embedder.onnx"));
        assertFalse(SequencePadder.isDynamicallyQuantized("not-a-local-file"));
    }

    private static SequencePadder padder(Integer... buckets) {
        return SequencePadder.of(SequencePadderOptions.of(List.of(buckets)), SEQUENCE_INPUTS, OUTPUTS, "model");
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
     * Outputs 'logits' with a value per real token, and 'last_hidden_state' with a fixed size, like a pooled output:
     * the given values for an unpadded request, and the padded values for a padded one, like a model that attends
     * to the padding if they differ.
     */
    private static class FakeEvaluation implements SequencePadder.Evaluation {

        private final double[] values;
        private final double[] paddedValues;
        final List<Request> requests = java.util.Collections.synchronizedList(new ArrayList<>());
        final List<Map<String, Tensor>> inputs = java.util.Collections.synchronizedList(new ArrayList<>());
        boolean cutOutputs = true;
        RuntimeException failure = null;
        boolean failUnpadded = false;
        /** Held during the verification runs, so that concurrent requests queue up */
        java.util.concurrent.CountDownLatch holdVerification = null;
        java.time.Duration evaluationDuration = java.time.Duration.ZERO;
        final java.util.concurrent.atomic.AtomicInteger concurrent = new java.util.concurrent.atomic.AtomicInteger();
        final java.util.concurrent.atomic.AtomicInteger maxConcurrent = new java.util.concurrent.atomic.AtomicInteger();

        /** Outputs 1 for each real token, multiplied by 1 + change when padded, a relative change of change. */
        FakeEvaluation(double change) {
            this(new double[] { 1, 1, 1, 1 }, new double[] { 1 + change, 1 + change, 1 + change, 1 + change });
        }

        FakeEvaluation(double[] values, double[] paddedValues) {
            this.values = values;
            this.paddedValues = paddedValues;
        }

        @Override
        public Map<String, Tensor> evaluate(Map<String, Tensor> inputs, Request request) {
            boolean verifying = requests.size() < 2;
            requests.add(request);
            this.inputs.add(inputs);
            if ( ! request.isPadded() && failUnpadded) throw new IllegalStateException("Inference failed");
            if (request.isPadded() && failure != null) throw failure;
            if (holdVerification != null && verifying) await(holdVerification);
            maxConcurrent.accumulateAndGet(concurrent.incrementAndGet(), Math::max);
            try {
                if ( ! evaluationDuration.isZero()) Thread.sleep(evaluationDuration.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                concurrent.decrementAndGet();
            }
            int length = request.isPadded() && ! cutOutputs ? request.paddedLength() : request.length();
            var output = Tensor.Builder.of(TensorType.fromSpec("tensor<float>(d0[1],d1[" + length + "])"));
            var tokenValues = request.isPadded() ? paddedValues : values;
            for (int i = 0; i < length; i++)
                output.cell(i >= request.length() ? 0 : i < tokenValues.length ? tokenValues[i] : 1, 0, i);
            return Map.of("logits", output.build(),
                          "last_hidden_state", Tensor.from("tensor<float>(d0[1],d1[16]):[1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16]"));
        }

        private static void await(java.util.concurrent.CountDownLatch latch) {
            try {
                latch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

    }

}
