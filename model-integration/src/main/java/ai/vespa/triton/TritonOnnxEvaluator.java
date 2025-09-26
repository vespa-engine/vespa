// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.triton;

import ai.vespa.modelintegration.evaluator.OnnxEvaluator;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * An ONNX evaluator that uses {@link TritonOnnxClient} to evaluate the model.
 *
 * @author bjorncs
 */
class TritonOnnxEvaluator implements OnnxEvaluator {
    private static final Logger log = Logger.getLogger(TritonOnnxEvaluator.class.getName());
    
    private final String modelName;
    private final TritonOnnxClient tritonClient;
    private final boolean isExplicitControlMode;
    
    private TritonOnnxClient.ModelMetadata modelMetadata;
    
    TritonOnnxEvaluator(TritonOnnxClient tritonClient, String modelName, boolean isExplicitControlMode) {
        this.modelName = modelName;
        this.tritonClient = tritonClient;
        this.isExplicitControlMode = isExplicitControlMode;
        loadModelIfNotReady();
    }
    
    private void loadModelIfNotReady() {
        if (isExplicitControlMode) {
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

    // Evaluate with optional retry in case the model is unloaded because of app redeployment or Triton server restart.
    private Map<String, Tensor> evaluate(Map<String, Tensor> inputs, boolean allowRetry) {
        try {
            return tritonClient.evaluate(modelName, modelMetadata, inputs);
        } catch (TritonOnnxClient.TritonException e) {
            if (allowRetry) {
                log.warning("Retrying to evaluate model: " + modelName);
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
        // Note: This is not safe if evaluator instances share the same underlying Triton model.
        if (isExplicitControlMode) {
            tritonClient.unloadModel(modelName);
        }
    }
}
