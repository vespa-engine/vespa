// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition;

import com.yahoo.config.FileReference;
import com.yahoo.path.Path;
import com.yahoo.tensor.TensorType;
import com.yahoo.vespa.model.AbstractService;
import com.yahoo.vespa.model.ml.OnnxModelInfo;
import com.yahoo.vespa.model.utils.FileSender;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A global ONNX model distributed using file distribution, similar to ranking constants.
 *
 * @author lesters
 */
public class OnnxModel {

    public enum PathType {FILE, URI};

    private final String name;
    private PathType pathType = PathType.FILE;
    private String path = null;
    private String fileReference = "";
    private OnnxModelInfo modelInfo = null;
    private Map<String, String> inputMap = new HashMap<>();
    private Map<String, String> outputMap = new HashMap<>();

    public OnnxModel(String name) {
        this.name = name;
    }

    public OnnxModel(String name, String fileName) {
        this(name);
        this.path = fileName;
        validate();
    }

    public void setFileName(String fileName) {
        Objects.requireNonNull(fileName, "Filename cannot be null");
        this.path = fileName;
        this.pathType = PathType.FILE;
    }

    public void setUri(String uri) {
        throw new IllegalArgumentException("URI for ONNX models are not currently supported");
    }

    public PathType getPathType() {
        return pathType;
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

    /** Initiate sending of this constant to some services over file distribution */
    public void sendTo(Collection<? extends AbstractService> services) {
        FileReference reference = (pathType == OnnxModel.PathType.FILE)
                                  ? FileSender.sendFileToServices(path, services)
                                  : FileSender.sendUriToServices(path, services);
        this.fileReference = reference.value();
    }

    public String getName() { return name; }
    public String getFileName() { return path; }
    public Path   getFilePath() { return Path.fromString(path); }
    public String getUri() { return path; }
    public String getFileReference() { return fileReference; }

    public Map<String, String> getInputMap() { return Collections.unmodifiableMap(inputMap); }
    public Map<String, String> getOutputMap() { return Collections.unmodifiableMap(outputMap); }

    public String getDefaultOutput() {
        return modelInfo != null ? modelInfo.getDefaultOutput() : "";
    }

    TensorType getTensorType(String onnxName, Map<String, TensorType> inputTypes) {
        return modelInfo != null ? modelInfo.getTensorType(onnxName, inputTypes) : TensorType.empty;
    }

    public void validate() {
        if (path == null || path.isEmpty())
            throw new IllegalArgumentException("ONNX models must have a file or uri.");
    }

    public String toString() {
        StringBuilder b = new StringBuilder();
        b.append("onnx-model '").append(name)
                .append(pathType == PathType.FILE ? "' from file '" : " from uri ").append(path)
                .append("' with ref '").append(fileReference)
                .append("'");
        return b.toString();
    }

}
