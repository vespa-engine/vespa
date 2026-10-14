// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.triton;

import ai.vespa.modelintegration.evaluator.OnnxEvaluator;
import ai.vespa.modelintegration.evaluator.SequencePadderOptions;
import ai.vespa.modelintegration.evaluator.SequencePadder;
import com.yahoo.jdisc.ResourceReference;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
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

    private final String modelName;
    private final ResourceReference modelReference;
    private final TritonOnnxClient tritonClient;
    private final boolean isExplicitControl;
    private final Optional<SequencePadder> padder; // Present when padding buckets are configured

    private TritonOnnxClient.ModelMetadata modelMetadata;

    TritonOnnxEvaluator(
            String modelName,
            ResourceReference modelReference,
            TritonOnnxClient tritonClient,
            boolean isExplicitControl) {
        this(modelName, modelReference, tritonClient, isExplicitControl, Optional.empty());
    }

    TritonOnnxEvaluator(
            String modelName,
            ResourceReference modelReference,
            TritonOnnxClient tritonClient,
            boolean isExplicitControl,
            Optional<SequencePadderOptions> paddingOptions) {
        this.modelName = modelName;
        this.modelReference = modelReference;
        this.tritonClient = tritonClient;
        this.isExplicitControl = isExplicitControl;
        modelMetadata = tritonClient.getModelMetadata(modelName);
        padder = paddingOptions.map(options -> SequencePadder.of(options, modelMetadata.inputs, modelMetadata.outputs, modelName));
        padder.ifPresent(padder -> padder.verify((inputs, request) -> evaluate(inputs, request, null)));
    }

    @Override
    public Tensor evaluate(Map<String, Tensor> inputs, String output, Duration timeout) {
        return evaluate(inputs, timeout).get(output);
    }

    @Override
    public Map<String, Tensor> evaluate(Map<String, Tensor> inputs, Duration timeout) {
        if (padder.isPresent()) return padder.get().evaluate(inputs, (unpadded, request) -> evaluate(unpadded, request, timeout));
        return reloadingOnFailure(() -> tritonClient.evaluate(modelName, modelMetadata, inputs, timeout));
    }

    private Map<String, Tensor> evaluate(Map<String, Tensor> inputs, SequencePadder.Request request, Duration timeout) {
        if ( ! request.isPadded()) return reloadingOnFailure(() -> tritonClient.evaluate(modelName, modelMetadata, inputs, timeout));
        var padder = this.padder.get();
        var padded = padder.pad(inputs, request);
        return reloadingOnFailure(() -> tritonClient.evaluate(modelName, modelMetadata, padded, timeout, padder, request));
    }

    private Map<String, Tensor> reloadingOnFailure(Supplier<Map<String, Tensor>> evaluation) {
        try {
            return evaluation.get();
        } catch (TritonOnnxClient.TritonException e) {
            if (!isExplicitControl) {
                throw e;
            }

            log.log(
                    Level.WARNING,
                    "Failed to evaluate ONNX model " + modelName + " in Triton, will retry after reload.",
                    e);
            tritonClient.loadUntilModelReady(modelName);
            modelMetadata = tritonClient.getModelMetadata(modelName);
            return evaluation.get();
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

    // For unit testing
    Optional<SequencePadder> padder() { return padder; }

    @Override
    public void close() {
        modelReference.close();
    }
}
