// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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
        try {
            environment = OrtEnvironment.getEnvironment();
            session = environment.createSession(modelPath, new OrtSession.SessionOptions());
        } catch (OrtException e) {
            throw new RuntimeException("ONNX Runtime exception", e);
        }
    }

    public Tensor evaluate(Map<String, Tensor> inputs, String output) {
        try {
            Map<String, OnnxTensor> onnxInputs = TensorConverter.toOnnxTensors(inputs, environment, session);
            try (OrtSession.Result result = session.run(onnxInputs, Collections.singleton(output))) {
                return TensorConverter.toVespaTensor(result.get(0));
            }
        } catch (OrtException e) {
            throw new RuntimeException("ONNX Runtime exception", e);
        }
    }

    public Map<String, Tensor> evaluate(Map<String, Tensor> inputs) {
        try {
            Map<String, OnnxTensor> onnxInputs = TensorConverter.toOnnxTensors(inputs, environment, session);
            Map<String, Tensor> outputs = new HashMap<>();
            try (OrtSession.Result result = session.run(onnxInputs)) {
                for (Map.Entry<String, OnnxValue> output : result) {
                    outputs.put(output.getKey(), TensorConverter.toVespaTensor(output.getValue()));
                }
                return outputs;
            }
        } catch (OrtException e) {
            throw new RuntimeException("ONNX Runtime exception", e);
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

}
