// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package ai.vespa.modelintegration.evaluator;

import ai.onnxruntime.NodeInfo;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OnnxValue;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import ai.vespa.modelintegration.evaluator.OnnxRuntime.ModelPathOrData;
import ai.vespa.modelintegration.evaluator.OnnxRuntime.ReferencedOrtSession;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static ai.vespa.modelintegration.evaluator.OnnxRuntime.isCudaError;


/**
 * Evaluates an ONNX Model by deferring to ONNX Runtime.
 *
 * @author lesters
 */
public class OnnxEvaluator implements AutoCloseable {

    private final ReferencedOrtSession session;

    OnnxEvaluator(String modelPath, OnnxEvaluatorOptions options, OnnxRuntime runtime) {
        session = createSession(ModelPathOrData.of(modelPath), runtime, options, true);
    }

    OnnxEvaluator(byte[] data, OnnxEvaluatorOptions options, OnnxRuntime runtime) {
        session = createSession(ModelPathOrData.of(data), runtime, options, true);
    }

    public Tensor evaluate(Map<String, Tensor> inputs, String output) {
        Map<String, OnnxTensor> onnxInputs = null;
        try {
            output = mapToInternalName(output);
            onnxInputs = TensorConverter.toOnnxTensors(inputs, OnnxRuntime.ortEnvironment(), session.instance());
            try (OrtSession.Result result = session.instance().run(onnxInputs, Collections.singleton(output))) {
                return TensorConverter.toVespaTensor(result.get(0));
            }
        } catch (OrtException e) {
            throw new RuntimeException("ONNX Runtime exception", e);
        } finally {
            if (onnxInputs != null) {
                onnxInputs.values().forEach(OnnxTensor::close);
            }
        }
    }

    public Map<String, Tensor> evaluate(Map<String, Tensor> inputs) {
        Map<String, OnnxTensor> onnxInputs = null;
        try {
            onnxInputs = TensorConverter.toOnnxTensors(inputs, OnnxRuntime.ortEnvironment(), session.instance());
            Map<String, Tensor> outputs = new HashMap<>();
            try (OrtSession.Result result = session.instance().run(onnxInputs)) {
                for (Map.Entry<String, OnnxValue> output : result) {
                    String mapped = TensorConverter.asValidName(output.getKey());
                    outputs.put(mapped, TensorConverter.toVespaTensor(output.getValue()));
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

    public record IdAndType(String id, TensorType type) { }

    private Map<String, IdAndType> toSpecMap(Map<String, NodeInfo> infoMap) {
        Map<String, IdAndType> result = new HashMap<>();
        for (var info : infoMap.entrySet()) {
            String name = info.getKey();
            String ident = TensorConverter.asValidName(name);
            TensorType t = TensorConverter.toVespaType(info.getValue().getInfo());
            result.put(name, new IdAndType(ident, t));
        }
        return result;
    }

    public Map<String, IdAndType> getInputs() {
        try {
            return toSpecMap(session.instance().getInputInfo());
        } catch (OrtException e) {
            throw new RuntimeException("ONNX Runtime exception", e);
        }
    }

    public Map<String, IdAndType> getOutputs() {
        try {
            return toSpecMap(session.instance().getOutputInfo());
        } catch (OrtException e) {
            throw new RuntimeException("ONNX Runtime exception", e);
        }
    }

    public Map<String, TensorType> getInputInfo() {
        try {
            return TensorConverter.toVespaTypes(session.instance().getInputInfo());
        } catch (OrtException e) {
            throw new RuntimeException("ONNX Runtime exception", e);
        }
    }

    public Map<String, TensorType> getOutputInfo() {
        try {
            return TensorConverter.toVespaTypes(session.instance().getOutputInfo());
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

    private static ReferencedOrtSession createSession(
            ModelPathOrData model, OnnxRuntime runtime, OnnxEvaluatorOptions options, boolean tryCuda) {
        if (options == null) {
            options = new OnnxEvaluatorOptions();
        }
        try {
            return runtime.acquireSession(model, options, tryCuda && options.requestingGpu());
        } catch (OrtException e) {
            if (e.getCode() == OrtException.OrtErrorCode.ORT_NO_SUCHFILE) {
                throw new IllegalArgumentException("No such file: " + model.path().get());
            }
            if (tryCuda && isCudaError(e) && !options.gpuDeviceRequired()) {
                // Failed in CUDA native code, but GPU device is optional, so we can proceed without it
                return createSession(model, runtime, options, false);
            }
            if (isCudaError(e)) {
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
