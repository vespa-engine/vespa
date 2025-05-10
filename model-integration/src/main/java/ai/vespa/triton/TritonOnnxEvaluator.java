// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.triton;

import ai.vespa.modelintegration.evaluator.OnnxEvaluator;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;

import java.util.HashMap;
import java.util.Map;

/**
 * An ONNX evaluator that uses {@link TritonOnnxClient} to evaluate the model.
 *
 * @author bjorncs
 */
class TritonOnnxEvaluator implements OnnxEvaluator {

    private final TritonOnnxClient triton;
    private final String modelName;
    private final TritonOnnxClient.ModelMetadata modelMetadata;
    private final boolean isExplicitControlMode;

    TritonOnnxEvaluator(TritonOnnxClient client, String modelName, boolean isExplicitControlMode) {
        this.modelName = modelName;
        this.triton = client;
        this.isExplicitControlMode = isExplicitControlMode;
        try {
            if (isExplicitControlMode) triton.loadModel(modelName);
            this.modelMetadata = triton.getModelMetadata(modelName);
        } catch (TritonOnnxClient.TritonException e) {
            throw new RuntimeException("Failed to load model: " + modelName, e);
        }
    }

    @Override
    public Tensor evaluate(Map<String, Tensor> inputs, String output) {
        try {
            return triton.evaluate(modelName, inputs, output);
        } catch (TritonOnnxClient.TritonException e) {
            throw new RuntimeException("Failed to evaluate model: " + modelName, e);
        }
    }

    @Override
    public Map<String, Tensor> evaluate(Map<String, Tensor> inputs) {
        try {
            return triton.evaluate(modelName, inputs);
        } catch (TritonOnnxClient.TritonException e) {
            throw new RuntimeException("Failed to evaluate model: " + modelName, e);
        }
    }

    @Override
    public Map<String, IdAndType> getInputs() {
        Map<String, IdAndType> result = new HashMap<>();
        modelMetadata.inputs().forEach((name, type) ->
            result.put(name, new IdAndType(name, type)));
        return result;
    }

    @Override
    public Map<String, IdAndType> getOutputs() {
        Map<String, IdAndType> result = new HashMap<>();
        modelMetadata.outputs().forEach((name, type) ->
            result.put(name, new IdAndType(name, type)));
        return result;
    }

    @Override
    public Map<String, TensorType> getInputInfo() {
        return modelMetadata.inputs();
    }

    @Override
    public Map<String, TensorType> getOutputInfo() {
        return modelMetadata.outputs();
    }

    @Override
    public void close() {
        // Note: This is not safe if evaluator instances shares the same underlying Triton model.
        if (isExplicitControlMode) triton.unloadModel(modelName);
    }
}
