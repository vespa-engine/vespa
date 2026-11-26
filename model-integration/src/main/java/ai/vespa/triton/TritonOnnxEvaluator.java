// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.triton;

import ai.vespa.modelintegration.evaluator.OnnxEvaluator;
import ai.vespa.modelintegration.evaluator.OnnxPoolingOptions;
import com.yahoo.jdisc.ResourceReference;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * An ONNX evaluator that uses Triton server for inference.
 *
 * @author bjorncs
 * @author glebashnik
 */
class TritonOnnxEvaluator implements OnnxEvaluator {
    private static final Logger log = Logger.getLogger(TritonOnnxEvaluator.class.getName());

    // Shared by all evaluators, so that a model gets one warning however many evaluators it has
    static final Set<String> modelsWarnedAboutMissingDeadline = ConcurrentHashMap.newKeySet();

    private final String modelName;
    private final ResourceReference modelReference;
    private final TritonOnnxClient onnxClient;
    private final TritonModelRepositoryClient repositoryClient;
    private final boolean isExplicitControl;
    private final Optional<OnnxPoolingOptions> runtimePooling;

    private TritonOnnxClient.ModelMetadata modelMetadata;

    TritonOnnxEvaluator(
            String modelName,
            ResourceReference modelReference,
            TritonOnnxClient onnxClient,
            TritonModelRepositoryClient repositoryClient,
            boolean isExplicitControl,
            Optional<OnnxPoolingOptions> runtimePooling) {
        this.modelName = modelName;
        this.modelReference = modelReference;
        this.onnxClient = onnxClient;
        this.repositoryClient = repositoryClient;
        this.isExplicitControl = isExplicitControl;
        this.runtimePooling = runtimePooling;
        modelMetadata = onnxClient.getModelMetadata(modelName);
        runtimePooling.ifPresent(this::requirePooled);
    }

    // A backend without pooling support ignores the pooling parameters and loads the model as is.
    private void requirePooled(OnnxPoolingOptions pooling) {
        var type = modelMetadata.outputs.get(pooling.outputName());
        if (type == null || type.dimensions().size() == 2) return; // A missing output is reported by the caller
        throw new IllegalArgumentException("Triton did not pool output '" + pooling.outputName() + "' of model " + modelName +
                                           ": got type " + type + " instead of [batch, embedding]. " +
                                           "The Triton image does not support pooling. Disable triton-onnx-pooling, " +
                                           "or use an image whose ONNX Runtime backend supports it.");
    }

    @Override
    public Tensor evaluate(Map<String, Tensor> inputs, String output) {
        warnOnceAboutMissingDeadline();
        return evaluate(inputs, output, Optional.empty());
    }

    @Override
    public Map<String, Tensor> evaluate(Map<String, Tensor> inputs) {
        warnOnceAboutMissingDeadline();
        return evaluate(inputs, Optional.empty());
    }

    @Override
    public Tensor evaluate(Map<String, Tensor> inputs, String output, Optional<Instant> deadline) {
        return evaluate(inputs, deadline).get(output);
    }

    @Override
    public Map<String, Tensor> evaluate(Map<String, Tensor> inputs, Optional<Instant> deadline) {
        Objects.requireNonNull(deadline, "deadline");
        try {
            return onnxClient.evaluate(modelName, modelMetadata, inputs, deadline);
        } catch (TritonException e) {
            if (!isExplicitControl) {
                throw e;
            }

            log.log(
                    Level.WARNING,
                    "Failed to evaluate ONNX model " + modelName + " in Triton, will retry after reload.",
                    e);
            repositoryClient.loadUntilModelReady(modelName);
            modelMetadata = onnxClient.getModelMetadata(modelName);
            return onnxClient.evaluate(modelName, modelMetadata, inputs, deadline);
        }
    }

    private void warnOnceAboutMissingDeadline() {
        if (modelsWarnedAboutMissingDeadline.add(modelName)) {
            log.warning("ONNX model " + modelName + " is evaluated without a deadline");
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
    public Optional<OnnxPoolingOptions> runtimePooling() {
        return runtimePooling;
    }

    @Override
    public void close() {
        modelReference.close();
    }
}
