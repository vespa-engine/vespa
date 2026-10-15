// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.embedding;

import ai.vespa.modelintegration.evaluator.EmbeddedOnnxRuntime;
import ai.vespa.modelintegration.evaluator.OnnxEvaluator;
import ai.vespa.modelintegration.evaluator.OnnxEvaluatorOptions;
import ai.vespa.modelintegration.evaluator.OnnxPadder;
import ai.vespa.modelintegration.evaluator.OnnxRuntime;
import ai.vespa.modelintegration.evaluator.config.OnnxEvaluatorConfig;
import com.yahoo.tensor.IndexedTensor;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Wraps the embedded runtime's evaluators in an OnnxPadder, as the Triton runtime does, over a recording of what
 * the model is asked to evaluate, and keeps the last so that the padding can be inspected by tests.
 *
 * @author glebashnik
 */
class PaddingTestRuntime implements OnnxRuntime {

    static final List<Integer> BUCKETS = List.of(16, 64);

    private final EmbeddedOnnxRuntime embedded = EmbeddedOnnxRuntime.createTestInstance();
    /** The evaluator the padder wraps, seeing the padded inputs */
    private RecordingEvaluator model;
    /** What the embedder got: the padder, or the model itself if padding was disabled */
    private OnnxEvaluator evaluator;

    /** Returns the config asking for the buckets, and the batching that makes padding worthwhile, for this runtime */
    static OnnxEvaluatorConfig onnxConfig(OnnxRuntime runtime) {
        var config = new OnnxEvaluatorConfig.Builder();
        if (runtime instanceof PaddingTestRuntime) config.batching.maxSize(8).paddingBuckets(BUCKETS);
        return config.build();
    }

    @Override
    public OnnxEvaluator evaluatorOf(String modelPath, OnnxEvaluatorOptions options) {
        model = new RecordingEvaluator(embedded.evaluatorOf(modelPath, options));
        evaluator = OnnxPadder.of(options.padding().orElseThrow(), model, modelPath);
        return evaluator;
    }

    boolean isPadding() { return evaluator instanceof OnnxPadder; }

    List<Integer> buckets() { return ((OnnxPadder) evaluator).buckets(); }

    /** The sequence lengths the model was asked to evaluate, in order */
    List<Integer> inputLengths() { return model.inputLengths; }

    int lastInputLength() { return model.inputLengths.get(model.inputLengths.size() - 1); }

    /** Records the sequence length of the inputs of each evaluation */
    private static class RecordingEvaluator implements OnnxEvaluator {

        private final OnnxEvaluator evaluator;
        final List<Integer> inputLengths = new ArrayList<>();

        RecordingEvaluator(OnnxEvaluator evaluator) { this.evaluator = evaluator; }

        private void record(Map<String, Tensor> inputs) {
            inputLengths.add((int) ((IndexedTensor) inputs.get("input_ids")).shape()[1]);
        }

        @Override
        public Tensor evaluate(Map<String, Tensor> inputs, String output, Duration timeout) {
            record(inputs);
            return evaluator.evaluate(inputs, output, timeout);
        }

        @Override
        public Map<String, Tensor> evaluate(Map<String, Tensor> inputs, Duration timeout) {
            record(inputs);
            return evaluator.evaluate(inputs, timeout);
        }

        @Override public Map<String, IdAndType> getInputs() { return evaluator.getInputs(); }
        @Override public Map<String, IdAndType> getOutputs() { return evaluator.getOutputs(); }
        @Override public Map<String, TensorType> getInputInfo() { return evaluator.getInputInfo(); }
        @Override public Map<String, TensorType> getOutputInfo() { return evaluator.getOutputInfo(); }
        @Override public void close() { evaluator.close(); }

    }

}
