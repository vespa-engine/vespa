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
        try {
            if (options == null) {
                options = new OnnxEvaluatorOptions();
            }
            environment = OrtEnvironment.getEnvironment();
            session = environment.createSession(modelPath, options.getOptions());
        } catch (OrtException e) {
            throw new RuntimeException("ONNX Runtime exception", e);
        }
    }

    public Tensor evaluate(Map<String, Tensor> inputs, String output) {
        Map<String, OnnxTensor> onnxInputs = null;
        try {
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
                    outputs.put(output.getKey(), TensorConverter.toVespaTensor(output.getValue()));
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

    public static boolean isRuntimeAvailable() {
        return isRuntimeAvailable("");
    }

    public static boolean isRuntimeAvailable(String modelPath) {
        try {
            new OnnxEvaluator(modelPath);
            return true;
        } catch (UnsatisfiedLinkError | RuntimeException | NoClassDefFoundError e) {
            return false;
        }
    }

}
