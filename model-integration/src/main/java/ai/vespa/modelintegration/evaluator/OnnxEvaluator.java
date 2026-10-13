// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package ai.vespa.modelintegration.evaluator;

import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;

import java.time.Duration;
import java.util.Map;

/**
 * Evaluator for ONNX models.
 *
 * @author bjorncs
 * @author glebashnik
 */
public interface OnnxEvaluator extends AutoCloseable {

    record IdAndType(String id, TensorType type) { }

    default Tensor evaluate(Map<String, Tensor> inputs, String output) {
        return evaluate(inputs, output, null);
    }

    default Map<String, Tensor> evaluate(Map<String, Tensor> inputs) {
        return evaluate(inputs, (Duration) null);
    }

    Tensor evaluate(Map<String, Tensor> inputs, String output, Duration timeout);
    Map<String, Tensor> evaluate(Map<String, Tensor> inputs, Duration timeout);

    Map<String, OnnxEvaluator.IdAndType> getInputs();
    Map<String, OnnxEvaluator.IdAndType> getOutputs();
    Map<String, TensorType> getInputInfo();
    Map<String, TensorType> getOutputInfo();

    /**
     * Returns whether callers should pad their inputs to a few fixed shapes: true when concurrent evaluations
     * with the same input shapes are combined into a single inference call, where this pays off,
     * and padding does not change the results.
     * This may inspect the model, so callers should only ask when they would pad.
     */
    default boolean shouldPadForBatching() { return false; }

    @Override void close();

}
