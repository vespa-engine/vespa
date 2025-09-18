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
 * @author glebashnik
 */
class TritonOnnxEvaluator implements OnnxEvaluator {
    private final String modelName;
    private final TritonOnnxClient tritonClient;
    private final TritonOnnxClient.ModelMetadata modelMetadata;
    private final TritonOnnxModelLoader modelLoader;

    TritonOnnxEvaluator(TritonOnnxClient tritonClient, String modelName, TritonOnnxModelLoader modelLoader) {
        this.modelName = modelName;
        this.tritonClient = tritonClient;
        this.modelLoader = modelLoader;
        modelMetadata = modelLoader.loadModel(modelName);
    }

    @Override
    public Tensor evaluate(Map<String, Tensor> inputs, String output) {
        try {
            return tritonClient.evaluate(modelName, inputs, output);
        } catch (TritonOnnxClient.TritonException e) {
            throw new RuntimeException("Failed to evaluate model: " + modelName, e);
        }
    }

    @Override
    public Map<String, Tensor> evaluate(Map<String, Tensor> inputs) {
        try {
            return tritonClient.evaluate(modelName, inputs);
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
        modelLoader.unloadModel(modelName);
    }
}
