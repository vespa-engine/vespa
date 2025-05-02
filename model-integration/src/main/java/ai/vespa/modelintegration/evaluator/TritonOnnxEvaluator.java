// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.modelintegration.evaluator;

import ai.vespa.llm.clients.NvidiaTriton;
import ai.vespa.llm.clients.TritonConfig;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;

import java.util.HashMap;
import java.util.Map;

/**
 * An ONNX evaluator that uses {@link ai.vespa.llm.clients.NvidiaTriton} to evaluate the model.
 *
 * @author bjorncs
 */
class TritonOnnxEvaluator implements OnnxEvaluator {

    private final NvidiaTriton triton;
    private final String modelName;
    private final NvidiaTriton.ModelMetadata modelMetadata;

    TritonOnnxEvaluator(TritonConfig config, String modelName) {
        this.modelName = modelName;
        this.triton = new NvidiaTriton(config);
        try {
            this.triton.loadModel(modelName);
            this.modelMetadata = triton.getModelMetadata(modelName);
        } catch (NvidiaTriton.TritonException e) {
            throw new RuntimeException("Failed to load model: " + modelName, e);
        }
    }

    @Override
    public Tensor evaluate(Map<String, Tensor> inputs, String output) {
        try {
            return triton.evaluate(modelName, inputs, output);
        } catch (NvidiaTriton.TritonException e) {
            throw new RuntimeException("Failed to evaluate model: " + modelName, e);
        }
    }

    @Override
    public Map<String, Tensor> evaluate(Map<String, Tensor> inputs) {
        try {
            return triton.evaluate(modelName, inputs);
        } catch (NvidiaTriton.TritonException e) {
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
        try {
            triton.close();
        } catch (Exception e) {
            throw new RuntimeException("Failed to close Triton client", e);
        }
    }
}
