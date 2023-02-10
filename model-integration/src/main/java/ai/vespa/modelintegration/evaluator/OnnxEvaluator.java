// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package ai.vespa.modelintegration.evaluator;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OnnxValue;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;


/**
 * Evaluates an ONNX Model by deferring to ONNX Runtime.
 *
 * @author lesters
 */
public class OnnxEvaluator {

    private final OrtEnvironment environment;
    private final OrtSession session;

    public OnnxEvaluator(String modelPath) {
        this(modelPath, null);
    }

    public OnnxEvaluator(String modelPath, OnnxEvaluatorOptions options) {
        environment = OrtEnvironment.getEnvironment();
        session = createSession(modelPath, environment, options, true);
    }

    public Tensor evaluate(Map<String, Tensor> inputs, String output) {
        Map<String, OnnxTensor> onnxInputs = null;
        try {
            output = mapToInternalName(output);
            onnxInputs = TensorConverter.toOnnxTensors(inputs, environment, session);
            try (OrtSession.Result result = session.run(onnxInputs, Collections.singleton(output))) {
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
            onnxInputs = TensorConverter.toOnnxTensors(inputs, environment, session);
            Map<String, Tensor> outputs = new HashMap<>();
            try (OrtSession.Result result = session.run(onnxInputs)) {
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

    public Map<String, TensorType> getInputInfo() {
        try {
            return TensorConverter.toVespaTypes(session.getInputInfo());
        } catch (OrtException e) {
            throw new RuntimeException("ONNX Runtime exception", e);
        }
    }

    public Map<String, TensorType> getOutputInfo() {
        try {
            return TensorConverter.toVespaTypes(session.getOutputInfo());
        } catch (OrtException e) {
            throw new RuntimeException("ONNX Runtime exception", e);
        }
    }

    private static OrtSession createSession(String modelPath, OrtEnvironment environment, OnnxEvaluatorOptions options, boolean tryCuda) {
        if (options == null) {
            options = new OnnxEvaluatorOptions();
        }
        try {
            return environment.createSession(modelPath, options.getOptions(tryCuda && options.requestingGpu()));
        } catch (OrtException e) {
            if (e.getCode() == OrtException.OrtErrorCode.ORT_NO_SUCHFILE) {
                throw new IllegalArgumentException("No such file: " + modelPath);
            }
            if (tryCuda && isCudaError(e) && !options.gpuDeviceRequired()) {
                // Failed in CUDA native code, but GPU device is optional, so we can proceed without it
                return createSession(modelPath, environment, options, false);
            }
            if (isCudaError(e)) {
                throw new IllegalArgumentException("GPU device is required, but CUDA initialization failed", e);
            }
            throw new RuntimeException("ONNX Runtime exception", e);
        }
    }

    private static boolean isCudaError(OrtException e) {
        return switch (e.getCode()) {
            case ORT_FAIL -> e.getMessage().contains("cudaError");
            case ORT_EP_FAIL -> e.getMessage().contains("Failed to find CUDA");
            default -> false;
        };
    }

    public static boolean isRuntimeAvailable() {
        return isRuntimeAvailable("");
    }

    public static boolean isRuntimeAvailable(String modelPath) {
        try {
            new OnnxEvaluator(modelPath);
            return true;
        } catch (IllegalArgumentException e) {
            if (e.getMessage().equals("No such file: ")) {
                return true;
            }
            return false;
        } catch (UnsatisfiedLinkError | RuntimeException | NoClassDefFoundError e) {
            return false;
        }
    }

    private String mapToInternalName(String outputName) throws OrtException {
        var info = session.getOutputInfo();
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
