// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema;

import com.yahoo.config.model.api.OnnxModelOptions;
import com.yahoo.searchlib.rankingexpression.Reference;
import com.yahoo.tensor.TensorType;
import com.yahoo.vespa.model.ml.OnnxModelInfo;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * A global ONNX model distributed using file distribution, similar to ranking constants.
 *
 * @author lesters
 */
public class OnnxModel extends DistributableResource implements Cloneable {

    // Model information
    private OnnxModelInfo modelInfo = null;
    private final Map<String, String> inputMap = new HashMap<>();
    private final Map<String, String> outputMap = new HashMap<>();
    private final Set<String> initializers = new HashSet<>();

    // Runtime options
    private OnnxModelOptions onnxModelOptions = OnnxModelOptions.empty();

    public OnnxModel(String name) {
        super(name);
    }

    public OnnxModel(String name, String fileName) {
        super(name, fileName);
        validate();
    }

    @Override
    public OnnxModel clone() {
        try {
            return (OnnxModel) super.clone(); // Shallow clone is sufficient here
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException("Clone not supported", e);
        }
    }

    @Override
    public void setUri(String uri) {
        throw new IllegalArgumentException("URI for ONNX models are not currently supported");
    }

    public void addInputNameMapping(String onnxName, String vespaName) {
        addInputNameMapping(onnxName, vespaName, true);
    }

    private String validateInputSource(String source) {
        var optRef = Reference.simple(source);
        if (optRef.isPresent()) {
            Reference ref = optRef.get();
            // input can be one of:
            // attribute(foo), query(foo), constant(foo)
            if (FeatureNames.isSimpleFeature(ref)) {
                return ref.toString();
            }
            // or a function (evaluated by backend)
            if (ref.isSimpleRankingExpressionWrapper()) {
                var arg = ref.simpleArgument();
                if (arg.isPresent()) {
                    return ref.toString();
                }
            }
        } else {
            // otherwise it must be an identifier
            Reference ref = Reference.fromIdentifier(source);
            return ref.toString();
        }
        // invalid input source
        throw new IllegalArgumentException("invalid input for ONNX model " + getName() + ": " + source);
    }

    public void addInputNameMapping(String onnxName, String vespaName, boolean overwrite) {
        Objects.requireNonNull(onnxName, "Onnx name cannot be null");
        Objects.requireNonNull(vespaName, "Vespa name cannot be null");
        String source = validateInputSource(vespaName);
        if (overwrite || ! inputMap.containsKey(onnxName)) {
            inputMap.put(onnxName, source);
        }
    }

    public void addOutputNameMapping(String onnxName, String vespaName) {
        addOutputNameMapping(onnxName, vespaName, true);
    }

    public void addOutputNameMapping(String onnxName, String vespaName, boolean overwrite) {
        Objects.requireNonNull(onnxName, "Onnx name cannot be null");
        Objects.requireNonNull(vespaName, "Vespa name cannot be null");
        // output name must be a valid identifier:
        var ref = Reference.fromIdentifier(vespaName);
        if (overwrite || ! outputMap.containsKey(onnxName)) {
            outputMap.put(onnxName, ref.toString());
        }
    }

    public void setModelInfo(OnnxModelInfo modelInfo) {
        Objects.requireNonNull(modelInfo, "Onnx model info cannot be null");
        for (String onnxName : modelInfo.getInputs()) {
            addInputNameMapping(onnxName, OnnxModelInfo.asValidIdentifier(onnxName), false);
        }
        for (String onnxName : modelInfo.getOutputs()) {
            addOutputNameMapping(onnxName, OnnxModelInfo.asValidIdentifier(onnxName), false);
        }
        initializers.addAll(modelInfo.getInitializers());
        this.modelInfo = modelInfo;
    }

    public Map<String, String> getInputMap() { return Collections.unmodifiableMap(inputMap); }
    public Map<String, String> getOutputMap() { return Collections.unmodifiableMap(outputMap); }
    public Set<String> getInitializers() { return Set.copyOf(initializers); }

    public String getDefaultOutput() {
        return modelInfo != null ? modelInfo.getDefaultOutput() : "";
    }

    TensorType getTensorType(String onnxName, Map<String, TensorType> inputTypes) {
        return modelInfo != null ? modelInfo.getTensorType(onnxName, inputTypes) : TensorType.empty;
    }

    public void setStatelessExecutionMode(String executionMode) {
        if ("parallel".equalsIgnoreCase(executionMode)) {
            onnxModelOptions = onnxModelOptions.withExecutionMode("parallel");
        } else if ("sequential".equalsIgnoreCase(executionMode)) {
            onnxModelOptions = onnxModelOptions.withExecutionMode("sequential");
        }
    }

    public Optional<String> getStatelessExecutionMode() {
        return onnxModelOptions.executionMode();
    }

    public void setStatelessInterOpThreads(int interOpThreads) {
        if (interOpThreads >= 0) {
            onnxModelOptions = onnxModelOptions.withInterOpThreads(interOpThreads);
        }
    }

    public Optional<Integer> getStatelessInterOpThreads() {
        return onnxModelOptions.interOpThreads();
    }

    public void setStatelessIntraOpThreads(int intraOpThreads) {
        if (intraOpThreads >= 0) {
            onnxModelOptions = onnxModelOptions.withIntraOpThreads(intraOpThreads);
        }
    }

    public Optional<Integer> getStatelessIntraOpThreads() {
        return onnxModelOptions.intraOpThreads();
    }

    public void setGpuDevice(int deviceNumber, boolean required) {
        if (deviceNumber >= 0) {
            onnxModelOptions = onnxModelOptions.withGpuDevice(new OnnxModelOptions.GpuDevice(deviceNumber, required));
        }
    }

    public Optional<OnnxModelOptions.GpuDevice> getGpuDevice() {
        return onnxModelOptions.gpuDevice();
    }

    public OnnxModelOptions onnxModelOptions() { return onnxModelOptions; }

}
