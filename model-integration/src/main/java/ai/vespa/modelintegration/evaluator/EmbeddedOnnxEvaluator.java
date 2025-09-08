// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.modelintegration.evaluator;

import ai.onnxruntime.NodeInfo;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OnnxValue;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import static com.yahoo.config.model.api.OnnxModelOptions.DimensionResolving;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * Evaluates an ONNX Model by deferring to ONNX Runtime.
 *
 * @author lesters
 */
class EmbeddedOnnxEvaluator implements OnnxEvaluator {

    private static final Logger LOG = Logger.getLogger(EmbeddedOnnxEvaluator.class.getName());

    private final EmbeddedOnnxRuntime.ReferencedOrtSession session;
    private final DimensionResolving dimensionResolving;

    EmbeddedOnnxEvaluator(String modelPath, OnnxEvaluatorOptions options, EmbeddedOnnxRuntime runtime) {
        if (options == null) {
            options = OnnxEvaluatorOptions.defaultOptions;
        }
        session = createSession(EmbeddedOnnxRuntime.ModelPathOrData.of(modelPath), runtime, options, true);
        dimensionResolving = options.dimensionResolving();
    }

    EmbeddedOnnxEvaluator(byte[] data, OnnxEvaluatorOptions options, EmbeddedOnnxRuntime runtime) {
        if (options == null) {
            options = OnnxEvaluatorOptions.defaultOptions;
        }
        session = createSession(EmbeddedOnnxRuntime.ModelPathOrData.of(data), runtime, options, true);
        dimensionResolving = options.dimensionResolving();
    }

    @Override
    public Tensor evaluate(Map<String, Tensor> inputs, String output) {
        Map<String, OnnxTensor> onnxInputs = null;
        try {
            output = mapToInternalName(output);
            onnxInputs = TensorConverter.toOnnxTensors(inputs, EmbeddedOnnxRuntime.ortEnvironment(), session.instance());
            try (OrtSession.Result result = session.instance().run(onnxInputs, Collections.singleton(output))) {
                return TensorConverter.toVespaTensor(result.get(0), dimensionResolving);
            }
        } catch (OrtException e) {
            throw new RuntimeException("ONNX Runtime exception", e);
        } finally {
            if (onnxInputs != null) {
                onnxInputs.values().forEach(OnnxTensor::close);
            }
        }
    }

    @Override
    public Map<String, Tensor> evaluate(Map<String, Tensor> inputs) {
        Map<String, OnnxTensor> onnxInputs = null;
        try {
            onnxInputs = TensorConverter.toOnnxTensors(inputs, EmbeddedOnnxRuntime.ortEnvironment(), session.instance());
            Map<String, Tensor> outputs = new HashMap<>();
            try (OrtSession.Result result = session.instance().run(onnxInputs)) {
                for (Map.Entry<String, OnnxValue> output : result) {
                    String mapped = TensorConverter.asValidName(output.getKey());
                    outputs.put(mapped, TensorConverter.toVespaTensor(output.getValue(), dimensionResolving));
                }
                return outputs;
            }
        } catch (OrtException e) {
            throw new RuntimeException("ONNX Runtime exception", e);
        } finally {
            if (onnxInputs != null) {
                onnxInputs.values().forEach(OnnxTensor::close);
            }
        }
    }

    private Map<String, OnnxEvaluator.IdAndType> toSpecMap(Map<String, NodeInfo> infoMap) {
        Map<String, OnnxEvaluator.IdAndType> result = new HashMap<>();
        for (var info : infoMap.entrySet()) {
            String name = info.getKey();
            String ident = TensorConverter.asValidName(name);
            TensorType t = TensorConverter.toVespaType(info.getValue().getInfo(), dimensionResolving);
            result.put(name, new OnnxEvaluator.IdAndType(ident, t));
        }
        return result;
    }

    @Override
    public Map<String, OnnxEvaluator.IdAndType> getInputs() {
        try {
            return toSpecMap(session.instance().getInputInfo());
        } catch (OrtException e) {
            throw new RuntimeException("ONNX Runtime exception", e);
        }
    }

    @Override
    public Map<String, OnnxEvaluator.IdAndType> getOutputs() {
        try {
            return toSpecMap(session.instance().getOutputInfo());
        } catch (OrtException e) {
            throw new RuntimeException("ONNX Runtime exception", e);
        }
    }

    @Override
    public Map<String, TensorType> getInputInfo() {
        try {
            return TensorConverter.toVespaTypes(session.instance().getInputInfo(), dimensionResolving);
        } catch (OrtException e) {
            throw new RuntimeException("ONNX Runtime exception", e);
        }
    }

    @Override
    public Map<String, TensorType> getOutputInfo() {
        try {
            return TensorConverter.toVespaTypes(session.instance().getOutputInfo(), dimensionResolving);
        } catch (OrtException e) {
            throw new RuntimeException("ONNX Runtime exception", e);
        }
    }

    @Override
    public void close() throws IllegalStateException {
        try {
            session.close();
        } catch (UncheckedOrtException e) {
            throw new IllegalStateException("Failed to close ONNX session", e);
        } catch (IllegalStateException e) {
            throw new IllegalStateException("Already closed", e);
        }
    }

    private static EmbeddedOnnxRuntime.ReferencedOrtSession createSession(
            EmbeddedOnnxRuntime.ModelPathOrData model,
            EmbeddedOnnxRuntime runtime,
            OnnxEvaluatorOptions options,
            boolean tryCuda) {
        Objects.requireNonNull(options, "options cannot be null");
        try {
            boolean loadCuda = tryCuda && options.requestingGpu();
            EmbeddedOnnxRuntime.ReferencedOrtSession session = runtime.acquireSession(model, options, loadCuda);
            if (loadCuda) {
                LOG.log(Level.INFO, "Created session with CUDA using GPU device " + options.gpuDeviceNumber());
            }
            return session;
        } catch (OrtException e) {
            if (e.getCode() == OrtException.OrtErrorCode.ORT_NO_SUCHFILE) {
                throw new IllegalArgumentException("No such file: " + model.path().get());
            }
            if (tryCuda && EmbeddedOnnxRuntime.isCudaError(e) && !options.gpuDeviceRequired()) {
                LOG.log(Level.INFO, "Failed to create session with CUDA using GPU device " +
                        options.gpuDeviceNumber() + ". Falling back to CPU. Reason: " + e.getMessage());
                return createSession(model, runtime, options, false);
            }
            if (EmbeddedOnnxRuntime.isCudaError(e)) {
                throw new IllegalArgumentException("GPU device is required, but CUDA initialization failed", e);
            }
            throw new RuntimeException("ONNX Runtime exception", e);
        }
    }

    // For unit testing
    OrtSession ortSession() { return session.instance(); }

    private String mapToInternalName(String outputName) throws OrtException {
        var info = session.instance().getOutputInfo();
        var internalNames = info.keySet();
        for (String name : internalNames) {
            if (name.equals(outputName)) {
                return name;
            }
        }
        for (String name : internalNames) {
            String mapped = TensorConverter.asValidName(name);
            if (mapped.equals(outputName)) {
                return name;
            }
        }
        // Probably will not work, but give the correct error from session.run
        return outputName;
    }

}
