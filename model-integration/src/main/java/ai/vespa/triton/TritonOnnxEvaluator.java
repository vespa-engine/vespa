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
    
    TritonOnnxEvaluator(String modelName, ResourceReference modelReference, TritonOnnxClient tritonClient, boolean isExplicitControl) {
        this.modelName = modelName;
        this.modelReference = modelReference;
        this.tritonClient = tritonClient;
        this.isExplicitControl = isExplicitControl;
        loadModelIfNotReady();
    }
    
    private void loadModelIfNotReady() {
        if (isExplicitControl) {
            var isModelReady = tritonClient.isModelReady(modelName);

            if (!isModelReady) {
                tritonClient.loadModel(modelName);
            }
        }
        
        modelMetadata = tritonClient.getModelMetadata(modelName);
    }

    @Override
    public Tensor evaluate(Map<String, Tensor> inputs, String output) {
        return evaluate(inputs).get(output);
    }

    @Override
    public Map<String, Tensor> evaluate(Map<String, Tensor> inputs) {
        return evaluate(inputs, true);
    }

    // Evaluate with optional retry that reloads the model if it is not ready.
    // This helps when models are unloaded after Triton server restart.
    private Map<String, Tensor> evaluate(Map<String, Tensor> inputs, boolean allowRetry) {
        try {
            return tritonClient.evaluate(modelName, modelMetadata, inputs);
        } catch (TritonOnnxClient.TritonException e) {
            if (allowRetry) {
                log.log(Level.WARNING, "Failed to evaluate ONNX model in Trion. Will retry after reload. Model: " + modelName, e);
                loadModelIfNotReady();
                return evaluate(inputs, false);
            }

            throw e;
        }
    }

    @Override
    public Map<String, IdAndType> getInputs() {
        Map<String, IdAndType> result = new HashMap<>();
        modelMetadata.inputs.forEach((name, type) ->
            result.put(name, new IdAndType(name, type)));
        return result;
    }

    @Override
    public Map<String, IdAndType> getOutputs() {
        Map<String, IdAndType> result = new HashMap<>();
        modelMetadata.outputs.forEach((name, type) ->
            result.put(name, new IdAndType(name, type)));
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
