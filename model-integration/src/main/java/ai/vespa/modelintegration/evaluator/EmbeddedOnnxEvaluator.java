// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.modelintegration.evaluator;

import ai.onnxruntime.NodeInfo;
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

import static com.yahoo.protect.Process.logAndDie;


/**
 * Evaluates an ONNX Model by deferring to ONNX Runtime.
 *
 * @author lesters
 * @author bjorncs
 */
class EmbeddedOnnxEvaluator implements OnnxEvaluator {

    private final EmbeddedOnnxRuntime.ReferencedOrtSession session;
    private final OrtEnvironment ortEnvironment;
    private final Map<String, OnnxEvaluator.IdAndType> inputs;
    private final Map<String, OnnxEvaluator.IdAndType> outputs;
    private final Map<String, TensorType> inputTypes;
    private final Map<String, TensorType> outputTypes;
    private final Map<String, String> outputNameMapping;

    EmbeddedOnnxEvaluator(EmbeddedOnnxRuntime.ReferencedOrtSession session, OrtEnvironment ortEnvironment) {
        this.session = session;
        this.ortEnvironment = ortEnvironment;
        try {
            var inputInfo = session.instance().getInputInfo();
            var outputInfo = session.instance().getOutputInfo();
            this.inputs = toSpecMap(inputInfo);
            this.outputs = toSpecMap(outputInfo);
            this.inputTypes = TensorConverter.toVespaTypes(inputInfo);
            this.outputTypes = TensorConverter.toVespaTypes(outputInfo);
            this.outputNameMapping = createOutputNameMapping(outputInfo);
        } catch (OrtException e) {
            throw handleOrtException(e);
        }
    }

    @Override
    public Tensor evaluate(Map<String, Tensor> inputs, String output) {
        Map<String, OnnxTensor> onnxInputs = null;
        try {
            output = mapToInternalName(output);
            onnxInputs = TensorConverter.toOnnxTensors(inputs, ortEnvironment, session.instance());
            try (OrtSession.Result result = session.instance().run(onnxInputs, Collections.singleton(output))) {
                return TensorConverter.toVespaTensor(result.get(0));
            }
        } catch (OrtException e) {
            throw handleOrtException(e);
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
            onnxInputs = TensorConverter.toOnnxTensors(inputs, ortEnvironment, session.instance());
            Map<String, Tensor> outputs = new HashMap<>();
            try (OrtSession.Result result = session.instance().run(onnxInputs)) {
                for (Map.Entry<String, OnnxValue> output : result) {
                    String mapped = TensorConverter.asValidName(output.getKey());
                    outputs.put(mapped, TensorConverter.toVespaTensor(output.getValue()));
                }
                return outputs;
            }
        } catch (OrtException e) {
            throw handleOrtException(e);
        } finally {
            if (onnxInputs != null) {
                onnxInputs.values().forEach(OnnxTensor::close);
            }
        }
    }
    
    private OnnxRuntimeException handleOrtException(OrtException exception) {
        if (exception.getMessage().contains("Failed to allocate memory")) {
            var device = session.cudaLoaded() ? "GPU" : "CPU";
            var message = "ONNX Runtime is out of memory during evaluation on " + device;
            logAndDie(message, exception);
        }

        return new OnnxRuntimeException("ONNX Runtime exception", exception);
    }

    @Override
    public Map<String, OnnxEvaluator.IdAndType> getInputs() { return inputs; }

    @Override
    public Map<String, OnnxEvaluator.IdAndType> getOutputs() { return outputs; }

    @Override
    public Map<String, TensorType> getInputInfo() { return inputTypes; }

    @Override
    public Map<String, TensorType> getOutputInfo() { return outputTypes; }

    @Override
    public void close() throws IllegalStateException {
        try {
            session.close();
        } catch (OnnxRuntimeException e) {
            throw new IllegalStateException("Failed to close ONNX session", e);
        } catch (IllegalStateException e) {
            throw new IllegalStateException("Already closed", e);
        }
    }

    // For unit testing
    OrtSession ortSession() { return session.instance(); }

    private String mapToInternalName(String outputName) {
        return outputNameMapping.getOrDefault(outputName, outputName);
    }

    private static Map<String, String> createOutputNameMapping(Map<String, NodeInfo> outputInfo) {
        Map<String, String> mapping = new HashMap<>();
        for (String internalName : outputInfo.keySet()) {
            // Map both the internal name and the mapped name to the internal name
            mapping.put(internalName, internalName);
            String mappedName = TensorConverter.asValidName(internalName);
            mapping.put(mappedName, internalName);
        }
        return Map.copyOf(mapping);
    }

    private static Map<String, OnnxEvaluator.IdAndType> toSpecMap(Map<String, NodeInfo> infoMap) {
        Map<String, OnnxEvaluator.IdAndType> result = new HashMap<>();
        for (var info : infoMap.entrySet()) {
            String name = info.getKey();
            String ident = TensorConverter.asValidName(name);
            TensorType t = TensorConverter.toVespaType(info.getValue().getInfo());
            result.put(name, new OnnxEvaluator.IdAndType(ident, t));
        }
        return Map.copyOf(result);
    }

}
