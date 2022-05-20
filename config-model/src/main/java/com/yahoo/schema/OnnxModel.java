// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema;

import com.yahoo.tensor.TensorType;
import com.yahoo.vespa.model.ml.OnnxModelInfo;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * A global ONNX model distributed using file distribution, similar to ranking constants.
 *
 * @author lesters
 */
public class OnnxModel extends DistributableResource {

    private OnnxModelInfo modelInfo = null;
    private final Map<String, String> inputMap = new HashMap<>();
    private final Map<String, String> outputMap = new HashMap<>();

    private String  statelessExecutionMode = null;
    private Integer statelessInterOpThreads = null;
    private Integer statelessIntraOpThreads = null;

    public OnnxModel(String name) {
        super(name);
    }

    public OnnxModel(String name, String fileName) {
        super(name, fileName);
        validate();
    }

    @Override
    public void setUri(String uri) {
        throw new IllegalArgumentException("URI for ONNX models are not currently supported");
    }

    public void addInputNameMapping(String onnxName, String vespaName) {
        addInputNameMapping(onnxName, vespaName, true);
    }

    public void addInputNameMapping(String onnxName, String vespaName, boolean overwrite) {
        Objects.requireNonNull(onnxName, "Onnx name cannot be null");
        Objects.requireNonNull(vespaName, "Vespa name cannot be null");
        if (overwrite || ! inputMap.containsKey(onnxName)) {
            inputMap.put(onnxName, vespaName);
        }
    }

    public void addOutputNameMapping(String onnxName, String vespaName) {
        addOutputNameMapping(onnxName, vespaName, true);
    }

    public void addOutputNameMapping(String onnxName, String vespaName, boolean overwrite) {
        Objects.requireNonNull(onnxName, "Onnx name cannot be null");
        Objects.requireNonNull(vespaName, "Vespa name cannot be null");
        if (overwrite || ! outputMap.containsKey(onnxName)) {
            outputMap.put(onnxName, vespaName);
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
        this.modelInfo = modelInfo;
    }

    public Map<String, String> getInputMap() { return Collections.unmodifiableMap(inputMap); }
    public Map<String, String> getOutputMap() { return Collections.unmodifiableMap(outputMap); }

    public String getDefaultOutput() {
        return modelInfo != null ? modelInfo.getDefaultOutput() : "";
    }

    TensorType getTensorType(String onnxName, Map<String, TensorType> inputTypes) {
        return modelInfo != null ? modelInfo.getTensorType(onnxName, inputTypes) : TensorType.empty;
    }

    public void setStatelessExecutionMode(String executionMode) {
        if ("parallel".equalsIgnoreCase(executionMode)) {
            this.statelessExecutionMode = "parallel";
        } else if ("sequential".equalsIgnoreCase(executionMode)) {
            this.statelessExecutionMode = "sequential";
        }
    }

    public Optional<String> getStatelessExecutionMode() {
        return Optional.ofNullable(statelessExecutionMode);
    }

    public void setStatelessInterOpThreads(int interOpThreads) {
        if (interOpThreads >= 0) {
            this.statelessInterOpThreads = interOpThreads;
        }
    }

    public Optional<Integer> getStatelessInterOpThreads() {
        return Optional.ofNullable(statelessInterOpThreads);
    }

    public void setStatelessIntraOpThreads(int intraOpThreads) {
        if (intraOpThreads >= 0) {
            this.statelessIntraOpThreads = intraOpThreads;
        }
    }

    public Optional<Integer> getStatelessIntraOpThreads() {
        return Optional.ofNullable(statelessIntraOpThreads);
    }

}
