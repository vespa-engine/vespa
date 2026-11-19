// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.models.evaluation;

import ai.vespa.modelintegration.evaluator.OnnxEvaluator;
import ai.vespa.modelintegration.evaluator.OnnxEvaluatorOptions;
import ai.vespa.modelintegration.evaluator.OnnxRuntime;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Evaluates with a given runtime, or throws {@link #failure} when it is set.
 * Records the deadline of each evaluation, and counts the evaluations called without a deadline.
 */
public class DeadlineRecordingRuntime implements OnnxRuntime {

    private final OnnxRuntime runtime;
    public final List<Optional<Instant>> deadlines = new ArrayList<>();
    public int callsWithoutDeadline = 0;
    public RuntimeException failure = null;

    public DeadlineRecordingRuntime(OnnxRuntime runtime) { this.runtime = runtime; }

    @Override
    public OnnxEvaluator evaluatorOf(String modelPath, OnnxEvaluatorOptions options) {
        var evaluator = runtime.evaluatorOf(modelPath, options);
        return new OnnxEvaluator() {
            @Override
            public Tensor evaluate(Map<String, Tensor> inputs, String output) {
                callsWithoutDeadline++;
                if (failure != null) throw failure;
                return evaluator.evaluate(inputs, output);
            }

            @Override
            public Map<String, Tensor> evaluate(Map<String, Tensor> inputs) {
                callsWithoutDeadline++;
                if (failure != null) throw failure;
                return evaluator.evaluate(inputs);
            }

            @Override
            public Tensor evaluate(Map<String, Tensor> inputs, String output, Optional<Instant> deadline) {
                deadlines.add(deadline);
                if (failure != null) throw failure;
                return evaluator.evaluate(inputs, output, deadline);
            }

            @Override
            public Map<String, Tensor> evaluate(Map<String, Tensor> inputs, Optional<Instant> deadline) {
                deadlines.add(deadline);
                if (failure != null) throw failure;
                return evaluator.evaluate(inputs, deadline);
            }

            @Override public Map<String, IdAndType> getInputs() { return evaluator.getInputs(); }
            @Override public Map<String, IdAndType> getOutputs() { return evaluator.getOutputs(); }
            @Override public Map<String, TensorType> getInputInfo() { return evaluator.getInputInfo(); }
            @Override public Map<String, TensorType> getOutputInfo() { return evaluator.getOutputInfo(); }
            @Override public void close() { evaluator.close(); }
        };
    }

}
