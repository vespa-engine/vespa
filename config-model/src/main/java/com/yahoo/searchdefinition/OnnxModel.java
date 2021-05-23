// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition;

import com.yahoo.tensor.TensorType;
import com.yahoo.vespa.model.ml.OnnxModelInfo;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A global ONNX model distributed using file distribution, similar to ranking constants.
 *
 * @author lesters
 */
public class OnnxModel extends DistributableResource {

    private OnnxModelInfo modelInfo = null;
    private Map<String, String> inputMap = new HashMap<>();
    private Map<String, String> outputMap = new HashMap<>();

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
}
