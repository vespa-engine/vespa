// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.ml;

import com.yahoo.json.Jackson;
import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.yahoo.config.application.api.ApplicationFile;
import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.io.IOUtils;
import com.yahoo.path.Path;
import com.yahoo.tensor.TensorType;
import onnx.Onnx;
import static com.yahoo.config.model.api.OnnxModelOptions.DimensionResolving;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Model information (input and output types) for an ONNX model.
 * This encapsulates the difference between reading ONNX model information
 * - from a file application package, where we can read the ONNX model directly
 * - from a ZK application package, where the file is unavailable and models are read from
 *   generated files stored in file distribution or ZooKeeper.
 *
 * @author lesters
 */
public class OnnxModelInfo {

    private static final Logger log = Logger.getLogger(OnnxModelInfo.class.getName());

    private final ApplicationPackage app;
    private final String modelPath;
    private final String defaultOutput;
    private final Map<String, OnnxTypeInfo> inputs;
    private final Map<String, OnnxTypeInfo> outputs;
    private final Map<String, TensorType> vespaTypes = new HashMap<>();
    private final Set<String> initializers;
    private final DimensionResolving dimensionResolving;

    private OnnxModelInfo(ApplicationPackage app, String path,
                          Map<String, OnnxTypeInfo> inputs,
                          Map<String, OnnxTypeInfo> outputs,
                          Set<String> initializers, String defaultOutput, DimensionResolving dimensionResolving) {
        this.app = app;
        this.modelPath = path;
        this.inputs = Map.copyOf(inputs);
        this.outputs = Map.copyOf(outputs);
        this.defaultOutput = defaultOutput;
        this.initializers = Set.copyOf(initializers);
        this.dimensionResolving = dimensionResolving;
    }

    public String getModelPath() {
        return modelPath;
    }

    public Set<String> getInputs() {
        return inputs.keySet();
    }

    public Set<String> getOutputs() {
        return outputs.keySet();
    }

    public Set<String> getInitializers() { return initializers; }

    public String getDefaultOutput() {
        return defaultOutput;
    }

    /**
     * Return the tensor type for an ONNX model for the given context.
     * An ONNX model can have dynamic/symbolic dimension sizes. If so, the output
     * type depends on the input types for the given context (rank profile).
     */
    public TensorType getTensorType(String onnxName, Map<String, TensorType> inputTypes) {
        OnnxTypeInfo onnxTypeInfo = outputs.get(onnxName);
        if (onnxTypeInfo == null) {
            throw new IllegalArgumentException("Could not find type for output '" + onnxName + "'");
        }
        if (onnxTypeInfo.containsUnknownDimensionSizes()) {
            Set<Long> unboundSizes = new HashSet<>();
            Map<String, Long> symbolicSizes = new HashMap<>();
            resolveUnknownDimensionSizes(inputTypes, symbolicSizes, unboundSizes);

            TensorType type = TensorType.empty;
            if (!inputTypes.isEmpty() && onnxTypeInfo.needModelProbe(symbolicSizes)) {
                type = OnnxModelProbe.probeModel(app, Path.fromString(modelPath), onnxName, inputTypes);
            }
            if (type.equals(TensorType.empty)) {
                type = onnxTypeInfo.toVespaTensorType(symbolicSizes, unboundSizes);
            }
            System.err.println("[OMI] with unknown dims: " + onnxName + " -> " + type);
            return type;
        }
        var result = vespaTypes.computeIfAbsent(onnxName, v -> onnxTypeInfo.toVespaTensorType());
        System.err.println("[OMI] no unknown dims: " + onnxName + " -> " + result);
        return result;
    }

    private void resolveUnknownDimensionSizes(Map<String, TensorType> inputTypes,
                                              Map<String, Long> symbolicSizes,
                                              Set<Long> unboundSizes)
    {
        for (Map.Entry<String, OnnxTypeInfo> input : inputs.entrySet()) {
            String onnxName = input.getKey();
            OnnxTypeInfo onnxType = input.getValue();
            TensorType vespaType = inputTypes.get(onnxName);
            if (vespaType == null || vespaType.dimensions().size() != onnxType.dimensions().size()) {
                continue;
            }

            for (int i = 0; i < vespaType.dimensions().size(); ++i) {
                if (vespaType.dimensions().get(i).size().isEmpty()) {
                    continue;
                }
                Long size = vespaType.dimensions().get(i).size().get();

                // Handle dimensions with size -1 - typically batch dimensions
                if (onnxType.dimensions().get(i).getSize() == -1) {
                    unboundSizes.add(size);
                    if (unboundSizes.size() > 1) {
                        throw new IllegalArgumentException("Found conflicting sizes for unbound dimension " +
                                                           "for type '" + onnxType + "'");
                    }

                // Handle dimensions with symbolic names
                } else if (onnxType.dimensions().get(i).hasSymbolicName()) {
                    String symbolicName = onnxType.dimensions().get(i).getSymbolicName();
                    if (symbolicSizes.containsKey(symbolicName) && ! symbolicSizes.get(symbolicName).equals(size)) {
                        throw new IllegalArgumentException("Found conflicting sizes for symbolic dimension '" +
                                                           symbolicName + "' for input '" + onnxName + "'");
                    }
                    symbolicSizes.put(symbolicName, size);
                }
            }
        }
    }

    static public OnnxModelInfo load(String path, ApplicationPackage app, DimensionResolving dimResolve) {
        Path pathInApplicationPackage = Path.fromString(path);
        if (app.getFile(pathInApplicationPackage).exists()) {
            return loadFromFile(pathInApplicationPackage, app, dimResolve);
        }
        if (app.getFile(generatedModelInfoPath(pathInApplicationPackage)).exists()) {
            return loadFromGeneratedInfo(pathInApplicationPackage, app);
        }
        throw new IllegalArgumentException("Unable to find ONNX model '" +  path + "'");
    }

    static public boolean modelExists(String path, ApplicationPackage app) {
        Path pathInApplicationPackage = Path.fromString(path);
        if (app.getFile(pathInApplicationPackage).exists()) {
            return true;
        }
        if (app.getFile(generatedModelInfoPath(Path.fromString(path))).exists()) {
            return true;
        }
        return false;
    }

    static private OnnxModelInfo loadFromFile(Path path, ApplicationPackage app, DimensionResolving dimResolve) {
        try (InputStream inputStream = app.getFile(path).createInputStream()) {
            Onnx.ModelProto model = Onnx.ModelProto.parseFrom(inputStream);
            String json = onnxModelToJson(model, path, dimResolve);
            storeGeneratedInfo(json, path, app);
            return jsonToModelInfo(json, app);

        } catch (IOException e) {
            throw new IllegalArgumentException("Unable to parse ONNX model", e);
        }
    }

    static private OnnxModelInfo loadFromGeneratedInfo(Path path, ApplicationPackage app) {
        try {
            String json = readGeneratedInfo(path, app);
            return jsonToModelInfo(json, app);
        } catch (IOException e) {
            throw new IllegalArgumentException("Unable to parse ONNX model", e);
        }
    }

    static private String readGeneratedInfo(Path path, ApplicationPackage app) throws IOException {
        ApplicationFile file = app.getFile(generatedModelInfoPath(path));
        return IOUtils.readAll(file.createReader());
    }

    static private void storeGeneratedInfo(String json, Path path, ApplicationPackage app) throws IOException {
        IOUtils.writeFile(app.getFileReference(generatedModelInfoPath(path)), json, false);
    }

    static private Path generatedModelInfoPath(Path path) {
        String fileName = asValidIdentifier(path.getRelative()) + ".modelinfo.json";
        return ApplicationPackage.MODELS_GENERATED_REPLICATED_DIR.append(fileName);
    }

    static private String onnxModelToJson(Onnx.ModelProto model, Path path, DimensionResolving dimResolve) throws IOException {
        var initializerNames = model.getGraph().getInitializerList().stream()
                .map(Onnx.TensorProto::getName).collect(Collectors.toSet());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        JsonGenerator g = new JsonFactory().createGenerator(out, JsonEncoding.UTF8);
        g.writeStartObject();

        g.writeStringField("path", path.toString());
        g.writeArrayFieldStart("inputs");
        int skippedInput = 0;
        for (Onnx.ValueInfoProto valueInfo : model.getGraph().getInputList()) {
            if (initializerNames.contains(valueInfo.getName())) {
                log.fine(() -> "For '%s': skipping name '%s' as it's an initializer"
                        .formatted(path.getName(), valueInfo.getName()));
                ++skippedInput;
                continue;
            }
            onnxTypeToJson(g, valueInfo);
        }
        if (skippedInput > 0)
            log.info("For '%s': skipped %d inputs that were also listed in initializers"
                             .formatted(path.getName(), skippedInput));
        g.writeEndArray();

        g.writeArrayFieldStart("outputs");
        for (Onnx.ValueInfoProto valueInfo : model.getGraph().getOutputList()) {
            onnxTypeToJson(g, valueInfo);
        }
        g.writeEndArray();

        g.writeArrayFieldStart("initializers");
        for (Onnx.TensorProto initializers : model.getGraph().getInitializerList()) {
            g.writeStartObject();
            g.writeStringField("name", initializers.getName());
            g.writeEndObject();
        }
        g.writeEndArray();
        g.writeStringField("dimension_resolving", dimResolve.toString());
        g.writeEndObject();
        g.close();
        return out.toString();
    }

    static public OnnxModelInfo jsonToModelInfo(String json, ApplicationPackage app) throws IOException {
        JsonNode root = Jackson.mapper().readTree(json);
        Map<String, OnnxTypeInfo> inputs = new HashMap<>();
        Map<String, OnnxTypeInfo> outputs = new HashMap<>();
        Set<String> initializers = new HashSet<>();
        String defaultOutput = "";

        var drRoot = root.get("dimension_resolving");
        var dimResolve = (drRoot == null) ? DimensionResolving.D_NUMBERS : DimensionResolving.valueOf(drRoot.textValue());

        String path = null;
        if (root.has("path")) {
            path = root.get("path").textValue();
        }
        for (JsonNode input : root.get("inputs")) {
            inputs.put(input.get("name").textValue(), jsonToTypeInfo(input, dimResolve));
        }
        for (JsonNode output : root.get("outputs")) {
            outputs.put(output.get("name").textValue(), jsonToTypeInfo(output, dimResolve));
        }
        if (root.get("outputs").has(0)) {
            defaultOutput = root.get("outputs").get(0).get("name").textValue();
        }
        var initializerRoot = root.get("initializers");
        if (initializerRoot != null) {
            for (JsonNode initializer : initializerRoot) {
                initializers.add(initializer.get("name").textValue());
            }
        }
        return new OnnxModelInfo(app, path, inputs, outputs, initializers, defaultOutput, dimResolve);
    }

    static private void onnxTypeToJson(JsonGenerator g, Onnx.ValueInfoProto valueInfo) throws IOException {
        g.writeStartObject();
        g.writeStringField("name", valueInfo.getName());
        var elemType = Onnx.TensorProto.DataType.forNumber(valueInfo.getType().getTensorType().getElemType());
        g.writeStringField("type", onnxValueTypeToString(elemType));
        g.writeArrayFieldStart("dim");
        for (Onnx.TensorShapeProto.Dimension dim : valueInfo.getType().getTensorType().getShape().getDimList()) {
            g.writeStartObject();
            if (dim.hasDimParam()) {
                g.writeStringField("type", "param");
                g.writeStringField("size", dim.getDimParam());
            } else {
                g.writeStringField("type", "value");
                g.writeNumberField("size", dim.getDimValue());
            }
            g.writeEndObject();
        }
        g.writeEndArray();
        g.writeEndObject();
    }

    static private OnnxTypeInfo jsonToTypeInfo(JsonNode node, DimensionResolving dimResolve) {
        TensorType.Value valueType = stringToValueType(node.get("type").textValue());
        OnnxTypeInfo type = new OnnxTypeInfo(valueType, dimResolve);
        for (JsonNode dim : node.get("dim")) {
            if (dim.get("type").textValue().equals("param")) {
                type.addDimension(dim.get("size").textValue());
            } else {
                type.addDimension(dim.get("size").longValue());
            }
        }
        return type;
    }

    private static String onnxValueTypeToString(Onnx.TensorProto.DataType dataType) {
        return switch (dataType) {
            case FLOAT -> "float";
            case DOUBLE -> "double";
            // Imperfect conversion, for now:
            case BOOL -> "float";
            case INT8 -> "float";
            case INT16 -> "float";
            case INT32 -> "float";
            case INT64 -> "float";
            case UINT8 -> "float";
            case UINT16 -> "float";
            case UINT32 -> "float";
            case UINT64 -> "float";
            default -> throw new IllegalArgumentException("A ONNX tensor with data type " + dataType +
                    " cannot be converted to a Vespa tensor type");
        };
    }

    private static TensorType.Value stringToValueType(String type) {
        return switch (type) {
            case "float" -> TensorType.Value.FLOAT;
            case "double" -> TensorType.Value.DOUBLE;
            default -> throw new IllegalArgumentException("Unknown tensor value type: " + type);
        };
    }

    public static String asValidIdentifier(String str) {
        return str.replaceAll("[^\\w\\d\\$@_]", "_");
    }


    private static class OnnxTypeInfo {
        private final TensorType.Value valueType;
        private final List<OnnxDimensionInfo> dimensions = new ArrayList<>();
        private final DimensionResolving dimResolve;

        OnnxTypeInfo(TensorType.Value valueType, DimensionResolving dimResolve) {
            this.valueType = valueType;
            this.dimResolve = dimResolve;
        }

        void addDimension(long value) {
            dimensions.add(new OnnxDimensionInfo(value));
        }

        void addDimension(String param) {
            dimensions.add(new OnnxDimensionInfo(param));
        }

        boolean containsUnknownDimensionSizes() {
            return dimensions.stream().anyMatch(OnnxDimensionInfo::unknownDimensionSize);
        }

        TensorType.Value valueType() {
            return valueType;
        }

        List<OnnxDimensionInfo> dimensions() {
            return dimensions;
        }

        TensorType toVespaTensorType() {
            return toVespaTensorType(null, null);
        }

        TensorType toVespaTensorType(Map<String, Long> symbolicSizes, Set<Long> unboundSizes) {
            String dimensionPrefix = "d"; // standard naming convention: d0, d1, ...
            TensorType.Builder builder = new TensorType.Builder(valueType);
            for (int i = 0; i < dimensions.size(); ++ i) {
                String dimensionName = dimensionPrefix + i;
                OnnxDimensionInfo onnxDimension = dimensions.get(i);
                long onnxDimensionSize = onnxDimension.getSize();
                if (onnxDimension.hasSymbolicName() && symbolicSizes != null && symbolicSizes.containsKey(onnxDimension.getSymbolicName())) {
                    onnxDimensionSize = symbolicSizes.get(onnxDimension.getSymbolicName());
                    if (dimResolve != DimensionResolving.D_NUMBERS) {
                        dimensionName = onnxDimension.getSymbolicName();
                    }
                }
                if (onnxDimensionSize == 0 && symbolicSizes != null) {
                    // This is for the case where all symbolic dimensions have
                    // different names, but can be resolved to a single dimension size.
                    Set<Long> unknownSizes = new HashSet<>(symbolicSizes.values());
                    if (unknownSizes.size() == 1) {
                        onnxDimensionSize = unknownSizes.iterator().next();
                    }
                }
                if (onnxDimensionSize < 0 && unboundSizes != null && !unboundSizes.isEmpty()) {
                    onnxDimensionSize = unboundSizes.iterator().next();
                }
                if (onnxDimensionSize <= 0) {
                    return TensorType.empty;  // Unable to determine type - probably out of context
                }
                builder.indexed(dimensionName, onnxDimensionSize);
            }
            return builder.build();
        }

        boolean needModelProbe(Map<String, Long> symbolicSizes) {
            for (OnnxDimensionInfo onnxDimension : dimensions) {
                if (onnxDimension.hasSymbolicName()) {
                    if (symbolicSizes == null)
                        return true;
                    if ( ! symbolicSizes.containsKey(onnxDimension.getSymbolicName())) {
                        return true;
                    }
                } else if (onnxDimension.getSize() == 0) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public String toString() {
            return "(" + valueType.id() + ")" +
                   "[" + dimensions.stream().map(OnnxDimensionInfo::toString).collect(Collectors.joining(",")) + "]";
        }

    }

    private static class OnnxDimensionInfo {
        private final long   size;
        private final String symbolicName;

        OnnxDimensionInfo(long size) {
            this.size = size;
            this.symbolicName = null;
        }

        OnnxDimensionInfo(String symbolicName) {
            this.size = 0;
            this.symbolicName = symbolicName;
        }

        long getSize() {
            return size;
        }

        String getSymbolicName() {
            return symbolicName;
        }

        boolean hasSymbolicName() {
            return symbolicName != null;
        }

        boolean unknownDimensionSize() {
            return hasSymbolicName() || size <= 0;
        }

        @Override
        public String toString() {
            return hasSymbolicName() ? "\"" + symbolicName + "\"" : Long.toString(size);
        }
    }

}
