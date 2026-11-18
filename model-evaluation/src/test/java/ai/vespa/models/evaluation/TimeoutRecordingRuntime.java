// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.models.evaluation;

import ai.vespa.modelintegration.evaluator.OnnxEvaluator;
import ai.vespa.modelintegration.evaluator.OnnxEvaluatorOptions;
import ai.vespa.modelintegration.evaluator.OnnxRuntime;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Evaluates with a given runtime. Records the timeout of each evaluation, which may be null,
 * and counts the evaluations called without a timeout.
 */
public class TimeoutRecordingRuntime implements OnnxRuntime {

    private final OnnxRuntime runtime;
    public final List<Duration> timeouts = new ArrayList<>();
    public int callsWithoutTimeout = 0;

    public TimeoutRecordingRuntime(OnnxRuntime runtime) { this.runtime = runtime; }

    @Override
    public OnnxEvaluator evaluatorOf(String modelPath, OnnxEvaluatorOptions options) {
        var evaluator = runtime.evaluatorOf(modelPath, options);
        return new OnnxEvaluator() {
            @Override
            public Tensor evaluate(Map<String, Tensor> inputs, String output) {
                callsWithoutTimeout++;
                return evaluator.evaluate(inputs, output);
            }

            @Override
            public Map<String, Tensor> evaluate(Map<String, Tensor> inputs) {
                callsWithoutTimeout++;
                return evaluator.evaluate(inputs);
            }

            @Override
            public Tensor evaluate(Map<String, Tensor> inputs, String output, Duration timeout) {
                timeouts.add(timeout);
                return evaluator.evaluate(inputs, output, timeout);
            }

            @Override
            public Map<String, Tensor> evaluate(Map<String, Tensor> inputs, Duration timeout) {
                timeouts.add(timeout);
                return evaluator.evaluate(inputs, timeout);
            }

            @Override public Map<String, IdAndType> getInputs() { return evaluator.getInputs(); }
            @Override public Map<String, IdAndType> getOutputs() { return evaluator.getOutputs(); }
            @Override public Map<String, TensorType> getInputInfo() { return evaluator.getInputInfo(); }
            @Override public Map<String, TensorType> getOutputInfo() { return evaluator.getOutputInfo(); }
            @Override public void close() { evaluator.close(); }
        };
    }

}
