// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package ai.vespa.modelintegration.evaluator;

import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;

import java.util.Map;

/**
 * Evaluator for ONNX models.
 *
 * @author bjorncs
 */
public interface OnnxEvaluator extends AutoCloseable {

    record IdAndType(String id, TensorType type) { }

    Tensor evaluate(Map<String, Tensor> inputs, String output);
    Map<String, Tensor> evaluate(Map<String, Tensor> inputs);

    Map<String, OnnxEvaluator.IdAndType> getInputs();
    Map<String, OnnxEvaluator.IdAndType> getOutputs();
    Map<String, TensorType> getInputInfo();
    Map<String, TensorType> getOutputInfo();

    @Override void close();

}
