// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition;

import com.yahoo.config.FileReference;
import com.yahoo.path.Path;
import com.yahoo.searchlib.rankingexpression.ExpressionFunction;
import com.yahoo.searchlib.rankingexpression.Reference;
import com.yahoo.tensor.TensorType;
import com.yahoo.vespa.model.AbstractService;
import com.yahoo.vespa.model.utils.FileSender;
import onnx.Onnx;

import java.util.Collection;
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
public class OnnxModel {

    public enum PathType {FILE, URI};

    private final String name;
    private PathType pathType = PathType.FILE;
    private String path = null;
    private String fileReference = "";
    private String defaultOutput = null;
    private Map<String, String> inputMap = new HashMap<>();
    private Map<String, String> outputMap = new HashMap<>();

    private Map<String, Onnx.TypeProto> inputTypes = new HashMap<>();
    private Map<String, Onnx.TypeProto> outputTypes = new HashMap<>();
    private Map<String, TensorType>     vespaTypes = new HashMap<>();

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

    public void setDefaultOutput(String onnxName) {
        Objects.requireNonNull(onnxName, "Name cannot be null");
        this.defaultOutput = onnxName;
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

    public void addInputType(String onnxName, Onnx.TypeProto type) {
        Objects.requireNonNull(onnxName, "Onnx name cannot be null");
        Objects.requireNonNull(type, "Tensor type cannot be null");
        inputTypes.put(onnxName, type);
    }

    public void addOutputType(String onnxName, Onnx.TypeProto type) {
        Objects.requireNonNull(onnxName, "Onnx name cannot be null");
        Objects.requireNonNull(type, "Tensor type cannot be null");
        outputTypes.put(onnxName, type);
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
        return defaultOutput;
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

    /**
     * Return the tensor type for an ONNX model for the given context.
     * An ONNX model can have dynamic/symbolic dimension sizes. If so, the output
     * type depends on the input types for the given context (rank profile).
     */
    public TensorType getTensorType(String onnxName, MapEvaluationTypeContext context) {
        Onnx.TypeProto onnxOutputType = outputTypes.get(onnxName);
        if (onnxOutputType == null) {
            throw new IllegalArgumentException("Could not find type for output '" + onnxName + "' " + "in '" + name + "'");
        }
        if (containsSymbolicDimensionSizes(onnxOutputType)) {
            return getTensorTypeWithSymbolicDimensions(onnxOutputType, context);
        }
        return vespaTypes.computeIfAbsent(onnxName, v -> typeFrom(onnxOutputType));
    }

    private TensorType getTensorTypeWithSymbolicDimensions(Onnx.TypeProto onnxOutputType, MapEvaluationTypeContext context) {
        Map<String, Long> symbolicSizes = resolveSymbolicDimensionSizes(context);
        if (symbolicSizes.isEmpty()) {
            return TensorType.empty;  // Context is probably a rank profile not using this ONNX model
        }
        return typeFrom(onnxOutputType, symbolicSizes);
    }

    private Map<String, Long> resolveSymbolicDimensionSizes(MapEvaluationTypeContext context) {
        Map<String, Long> symbolicSizes = new HashMap<>();
        for (String onnxInputName : inputTypes.keySet()) {

            Onnx.TypeProto onnxType = inputTypes.get(onnxInputName);
            if ( ! containsSymbolicDimensionSizes(onnxType)) {
                continue;
            }

            Optional<TensorType> vespaType = resolveInputType(onnxInputName, context);
            if (vespaType.isEmpty()) {
                return Collections.emptyMap();
            }

            var onnxDimensions = onnxType.getTensorType().getShape().getDimList();
            var vespaDimensions = vespaType.get().dimensions();
            if (vespaDimensions.size() != onnxDimensions.size()) {
                return Collections.emptyMap();
            }

            for (int i = 0; i < vespaDimensions.size(); ++i) {
                if (vespaDimensions.get(i).size().isEmpty() || ! onnxDimensions.get(i).hasDimParam()) {
                    continue;
                }
                String symbolicName = onnxDimensions.get(i).getDimParam();
                Long size = vespaDimensions.get(i).size().get();
                if (symbolicSizes.containsKey(symbolicName) && ! symbolicSizes.get(symbolicName).equals(size)) {
                    throw new IllegalArgumentException("Found conflicting sizes for symbolic dimension " +
                            "'" + symbolicName + "' for input '" + onnxInputName + "' in ONNX model '" +  name + "'");
                }
                symbolicSizes.put(symbolicName, size);
            }
        }
        return symbolicSizes;
    }

    private Optional<TensorType> resolveInputType(String onnxInputName, MapEvaluationTypeContext context) {
        String source = inputMap.get(onnxInputName);
        if (source != null) {
            // Source is either a simple reference (query/attribute/constant)...
            Optional<Reference> reference = Reference.simple(source);
            if (reference.isPresent()) {
                return Optional.of(context.getType(reference.get()));
            }
            // ... or a function
            ExpressionFunction func = context.getFunction(source);
            if (func != null) {
                return Optional.of(func.getBody().type(context));
            }
        }
        return Optional.empty();  // if this context does not contain this input
    }

    private static boolean containsSymbolicDimensionSizes(Onnx.TypeProto type) {
        return type.getTensorType().getShape().getDimList().stream().anyMatch(d -> d.hasDimParam() && ! d.hasDimValue());
    }

    private static TensorType typeFrom(Onnx.TypeProto type) {
        return typeFrom(type, null);
    }

    private static TensorType typeFrom(Onnx.TypeProto type, Map<String, Long> symbolicSizes) {
        String dimensionPrefix = "d"; // standard naming convention: d0, d1, ...
        Onnx.TensorShapeProto shape = type.getTensorType().getShape();
        TensorType.Builder builder = new TensorType.Builder(toValueType(type.getTensorType().getElemType()));
        for (int i = 0; i < shape.getDimCount(); ++ i) {
            String dimensionName = dimensionPrefix + i;
            Onnx.TensorShapeProto.Dimension onnxDimension = shape.getDim(i);
            long onnxDimensionSize = onnxDimension.getDimValue();
            if (onnxDimension.hasDimParam() && symbolicSizes != null && symbolicSizes.containsKey(onnxDimension.getDimParam())) {
                onnxDimensionSize = symbolicSizes.get(onnxDimension.getDimParam());
            }
            if (onnxDimensionSize == 0 && symbolicSizes != null) {
                // This is for the case where all symbolic dimensions have
                // different names, but can be resolved to a single dimension size.
                Set<Long> unknownSizes = new HashSet<>(symbolicSizes.values());
                if (unknownSizes.size() == 1) {
                    onnxDimensionSize = unknownSizes.iterator().next();
                }
            }
            if (onnxDimensionSize <= 0) {
                throw new IllegalArgumentException("Unable to determine fixed dimension size when converting from " +
                        "ONNX type: " + type + " to Vespa tensor type.");
            }
            builder.indexed(dimensionName, onnxDimensionSize);
        }
        return builder.build();
    }

    private static TensorType.Value toValueType(Onnx.TensorProto.DataType dataType) {
        switch (dataType) {
            case FLOAT: return TensorType.Value.FLOAT;
            case DOUBLE: return TensorType.Value.DOUBLE;
            // Imperfect conversion, for now:
            case BOOL: return TensorType.Value.FLOAT;
            case INT8: return TensorType.Value.FLOAT;
            case INT16: return TensorType.Value.FLOAT;
            case INT32: return TensorType.Value.FLOAT;
            case INT64: return TensorType.Value.FLOAT;
            case UINT8: return TensorType.Value.FLOAT;
            case UINT16: return TensorType.Value.FLOAT;
            case UINT32: return TensorType.Value.FLOAT;
            case UINT64: return TensorType.Value.FLOAT;
            default: throw new IllegalArgumentException("A ONNX tensor with data type " + dataType +
                    " cannot be converted to a Vespa tensor type");
        }
    }

}
