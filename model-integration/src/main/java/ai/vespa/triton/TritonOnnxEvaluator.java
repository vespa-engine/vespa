// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.triton;

import ai.vespa.modelintegration.evaluator.OnnxEvaluator;
import com.yahoo.jdisc.ResourceReference;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * An ONNX evaluator that uses Triton server for inference.
 *
 * @author bjorncs
 */
class TritonOnnxEvaluator implements OnnxEvaluator {
    private static final Logger log = Logger.getLogger(TritonOnnxEvaluator.class.getName());

    private final String modelName;
    private final ResourceReference modelReference;
    private final TritonOnnxClient tritonClient;
    private final boolean isExplicitControl;

    private TritonOnnxClient.ModelMetadata modelMetadata;

    TritonOnnxEvaluator(
            String modelName,
            ResourceReference modelReference,
            TritonOnnxClient tritonClient,
            boolean isExplicitControl) {
        this.modelName = modelName;
        this.modelReference = modelReference;
        this.tritonClient = tritonClient;
        this.isExplicitControl = isExplicitControl;

        if (isExplicitControl) {
            tritonClient.loadUntilModelReady(modelName);
        }
    }

    @Override
    public Tensor evaluate(Map<String, Tensor> inputs, String output) {
        return evaluate(inputs).get(output);
    }

    @Override
    public Map<String, Tensor> evaluate(Map<String, Tensor> inputs) {
        try {
            return tritonClient.evaluate(modelName, modelMetadata, inputs);
        } catch (TritonOnnxClient.TritonException e) {
            if (!isExplicitControl) {
                throw e;
            }

            log.log(
                    Level.WARNING,
                    "Failed to evaluate ONNX model " + modelName + " in Trion, will retry after reload.",
                    e);
            tritonClient.loadUntilModelReady(modelName);
            return tritonClient.evaluate(modelName, modelMetadata, inputs);
        }
    }

    @Override
    public Map<String, IdAndType> getInputs() {
        Map<String, IdAndType> result = new HashMap<>();
        modelMetadata.inputs.forEach((name, type) -> result.put(name, new IdAndType(name, type)));
        return result;
    }

    @Override
    public Map<String, IdAndType> getOutputs() {
        Map<String, IdAndType> result = new HashMap<>();
        modelMetadata.outputs.forEach((name, type) -> result.put(name, new IdAndType(name, type)));
        return result;
    }

    @Override
    public Map<String, TensorType> getInputInfo() {
        return modelMetadata.inputs;
    }

    @Override
    public Map<String, TensorType> getOutputInfo() {
        return modelMetadata.outputs;
    }

    @Override
    public void close() {
        modelReference.close();
    }
}
