// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.modelintegration.evaluator;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.ExtensionRegistryLite;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.WireFormat;
import onnx.Onnx;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Parses an ONNX model from a stream to extract information without loading the entire model into memory.
 *
 * @author bjorncs
 * @author ingridhabrekke
 * @author glebashnik
 */
public class OnnxStreamParser {

    private OnnxStreamParser() {}

    /**
     * Operators that quantize their input with a scale computed from the input itself, at inference time.
     * In a batch, that input holds every request, so each request's result depends on the others in the batch.
     * The last two are ONNX Runtime's fused versions, found in models saved after optimization.
     */
    private static final Set<String> DYNAMIC_QUANTIZATION_OPERATORS =
            Set.of("DynamicQuantizeLinear", "DynamicQuantizeMatMul", "DynamicQuantizeLSTM");

    /** Returns whether the model quantizes values dynamically, see {@link #DYNAMIC_QUANTIZATION_OPERATORS}. */
    public static boolean usesDynamicQuantization(Path model) throws IOException {
        return getOperatorTypes(model).stream().anyMatch(DYNAMIC_QUANTIZATION_OPERATORS::contains);
    }

    /**
     * Returns whether every input and output of the model declares a batch dimension: a first dimension of variable size.
     * A model exported with a [1, features] input, for example, has a first dimension of fixed size instead.
     * Triton's ONNX Runtime backend refuses to batch a model without a batch dimension.
     * The backend judges the shapes that ONNX Runtime reports, so this method reads them the same way.
     */
    public static boolean declaresBatchDimension(Path model) throws IOException {
        try (var bis = new BufferedInputStream(Files.newInputStream(model))) {
            var stream = CodedInputStream.newInstance(bis);
            while (!stream.isAtEnd()) {
                var tag = stream.readTag();
                if (WireFormat.getTagFieldNumber(tag) == 0) break;
                if (WireFormat.getTagFieldNumber(tag) == Onnx.ModelProto.GRAPH_FIELD_NUMBER) {
                    var graph = new GraphInputsAndOutputs();
                    parseDelimited(stream, s -> parseGraphInputsAndOutputs(s, graph));
                    return graph.declaresBatchDimension();
                }
                stream.skipField(tag);
            }
            return true;
        }
    }

    // Holds only what decides batching.
    // The nodes and weights of a large model then stay out of memory.
    private static class GraphInputsAndOutputs {
        final List<Onnx.ValueInfoProto> inputs = new ArrayList<>();
        final List<Onnx.ValueInfoProto> outputs = new ArrayList<>();
        final Set<String> initializerNames = new HashSet<>();

        boolean declaresBatchDimension() {
            // Models may list initializers as inputs.
            // ONNX Runtime does not take them as inputs.
            return inputs.stream().filter(input -> !initializerNames.contains(input.getName()))
                           .allMatch(input -> OnnxStreamParser.declaresBatchDimension(input, true))
                   && outputs.stream().allMatch(output -> OnnxStreamParser.declaresBatchDimension(output, false));
        }
    }

    private static boolean declaresBatchDimension(Onnx.ValueInfoProto value, boolean isInput) {
        var type = value.getType();
        // Triton's ONNX Runtime backend rejects inputs and outputs that are not tensors, whatever the batching.
        if (!type.hasTensorType()) return true;
        // ONNX Runtime reports an input without a shape as having no dimensions.
        // It infers the shape of an output without one.
        if (!type.getTensorType().hasShape()) return !isInput;
        var shape = type.getTensorType().getShape();
        if (shape.getDimCount() == 0) return false;
        var first = shape.getDim(0);
        // Some converters write a variable size as -1.
        // ONNX Runtime treats -1 as a variable size.
        return !first.hasDimValue() || first.getDimValue() == -1;
    }

    private static void parseGraphInputsAndOutputs(CodedInputStream stream, GraphInputsAndOutputs graph) throws IOException {
        while (!stream.isAtEnd()) {
            var tag = stream.readTag();
            if (WireFormat.getTagFieldNumber(tag) == 0) return;
            switch (WireFormat.getTagFieldNumber(tag)) {
                case Onnx.GraphProto.INPUT_FIELD_NUMBER:
                    graph.inputs.add(readValueInfo(stream));
                    break;
                case Onnx.GraphProto.OUTPUT_FIELD_NUMBER:
                    graph.outputs.add(readValueInfo(stream));
                    break;
                case Onnx.GraphProto.INITIALIZER_FIELD_NUMBER:
                    parseDelimited(stream, s -> parseTensorName(s, graph.initializerNames));
                    break;
                case Onnx.GraphProto.SPARSE_INITIALIZER_FIELD_NUMBER:
                    parseDelimited(stream, s -> parseSparseTensorName(s, graph.initializerNames));
                    break;
                default:
                    stream.skipField(tag);
                    break;
            }
        }
    }

    // ONNX names a sparse tensor by its values tensor.
    private static void parseSparseTensorName(CodedInputStream stream, Set<String> names) throws IOException {
        while (!stream.isAtEnd()) {
            var tag = stream.readTag();
            if (WireFormat.getTagFieldNumber(tag) == 0) return;
            if (WireFormat.getTagFieldNumber(tag) == Onnx.SparseTensorProto.VALUES_FIELD_NUMBER) {
                parseDelimited(stream, s -> parseTensorName(s, names));
            } else {
                stream.skipField(tag);
            }
        }
    }

    private static Onnx.ValueInfoProto readValueInfo(CodedInputStream stream) throws IOException {
        var builder = Onnx.ValueInfoProto.newBuilder();
        stream.readMessage(builder, ExtensionRegistryLite.getEmptyRegistry());
        return builder.build();
    }

    // Initializer data can take gigabytes.
    // This method needs only the name, so it skips the data.
    private static void parseTensorName(CodedInputStream stream, Set<String> names) throws IOException {
        while (!stream.isAtEnd()) {
            var tag = stream.readTag();
            if (WireFormat.getTagFieldNumber(tag) == 0) return;
            if (WireFormat.getTagFieldNumber(tag) == Onnx.TensorProto.NAME_FIELD_NUMBER) {
                names.add(stream.readString());
            } else {
                stream.skipField(tag);
            }
        }
    }

    /**
     * Finds all external data locations in an ONNX model by streaming the model file.
     *
     * @return set of relative file locations for the external data references in the provided ONNX model.
     */
    public static Set<Path> getExternalDataLocations(Path model) throws IOException {
        try (var stream = new BufferedInputStream(Files.newInputStream(model))) {
            return getExternalDataLocations(stream);
        }
    }

    /**
     * Finds external data locations like {@link #getExternalDataLocations(Path)}, but returns none for files that
     * are not valid ONNX, leaving those for the runtime to report.
     */
    public static Set<Path> tryGetExternalDataLocations(Path model) throws IOException {
        try {
            return getExternalDataLocations(model);
        } catch (InvalidProtocolBufferException | InvalidPathException e) {
            return Set.of();
        }
    }

    /** Like {@link #tryGetExternalDataLocations(Path)}, reading the stream sequentially. */
    public static Set<Path> tryGetExternalDataLocations(InputStream model) throws IOException {
        try {
            return getExternalDataLocations(model);
        } catch (InvalidProtocolBufferException | InvalidPathException e) {
            return Set.of();
        }
    }

    private static Set<Path> getExternalDataLocations(InputStream model) throws IOException {
        var stream = CodedInputStream.newInstance(model);
        var externalDataPaths = new HashSet<Path>();
        while (!stream.isAtEnd()) {
            var tag = stream.readTag();
            if (WireFormat.getTagFieldNumber(tag) == 0) break;
            switch (WireFormat.getTagFieldNumber(tag)) {
                case Onnx.ModelProto.GRAPH_FIELD_NUMBER:
                    parseDelimited(stream, s -> parseGraphProto(s, externalDataPaths));
                    break;
                case Onnx.ModelProto.TRAINING_INFO_FIELD_NUMBER:
                    parseDelimited(stream, s -> parseTrainingInfoProto(s, externalDataPaths));
                    break;
                case Onnx.ModelProto.FUNCTIONS_FIELD_NUMBER:
                    parseDelimited(stream, s -> parseFunctionProto(s, externalDataPaths));
                    break;
                default:
                    stream.skipField(tag);
                    break;
            }
        }
        return Set.copyOf(externalDataPaths);
    }

    /**
     * Finds the operator types used in an ONNX model by streaming the model file,
     * including the nodes of subgraphs and model-local functions.
     *
     * @return the operator types of the nodes in the provided ONNX model, e.g. "MatMul"
     */
    public static Set<String> getOperatorTypes(Path model) throws IOException {
        try (var bis = new BufferedInputStream(Files.newInputStream(model))) {
            return getOperatorTypes(CodedInputStream.newInstance(bis));
        }
    }

    private static Set<String> getOperatorTypes(CodedInputStream stream) throws IOException {
        var operatorTypes = new HashSet<String>();
        while (!stream.isAtEnd()) {
            var tag = stream.readTag();
            if (WireFormat.getTagFieldNumber(tag) == 0) break;
            switch (WireFormat.getTagFieldNumber(tag)) {
                case Onnx.ModelProto.GRAPH_FIELD_NUMBER:
                    parseDelimited(stream, s -> parseGraphOperatorTypes(s, operatorTypes));
                    break;
                case Onnx.ModelProto.FUNCTIONS_FIELD_NUMBER:
                    parseDelimited(stream, s -> parseFunctionOperatorTypes(s, operatorTypes));
                    break;
                default:
                    stream.skipField(tag);
                    break;
            }
        }
        return Set.copyOf(operatorTypes);
    }

    private static void parseGraphOperatorTypes(CodedInputStream stream, Set<String> operatorTypes) throws IOException {
        while (!stream.isAtEnd()) {
            var tag = stream.readTag();
            if (WireFormat.getTagFieldNumber(tag) == 0) return;
            if (WireFormat.getTagFieldNumber(tag) == Onnx.GraphProto.NODE_FIELD_NUMBER) {
                parseDelimited(stream, s -> parseNodeOperatorTypes(s, operatorTypes));
            } else {
                stream.skipField(tag);
            }
        }
    }

    private static void parseFunctionOperatorTypes(CodedInputStream stream, Set<String> operatorTypes) throws IOException {
        while (!stream.isAtEnd()) {
            var tag = stream.readTag();
            if (WireFormat.getTagFieldNumber(tag) == 0) return;
            if (WireFormat.getTagFieldNumber(tag) == Onnx.FunctionProto.NODE_FIELD_NUMBER) {
                parseDelimited(stream, s -> parseNodeOperatorTypes(s, operatorTypes));
            } else {
                stream.skipField(tag);
            }
        }
    }

    private static void parseNodeOperatorTypes(CodedInputStream stream, Set<String> operatorTypes) throws IOException {
        while (!stream.isAtEnd()) {
            var tag = stream.readTag();
            if (WireFormat.getTagFieldNumber(tag) == 0) return;
            switch (WireFormat.getTagFieldNumber(tag)) {
                case Onnx.NodeProto.OP_TYPE_FIELD_NUMBER:
                    operatorTypes.add(stream.readString());
                    break;
                case Onnx.NodeProto.ATTRIBUTE_FIELD_NUMBER:
                    // Attributes may hold subgraphs, e.g. the branches of an If node
                    parseDelimited(stream, s -> parseAttributeOperatorTypes(s, operatorTypes));
                    break;
                default:
                    stream.skipField(tag);
                    break;
            }
        }
    }

    private static void parseAttributeOperatorTypes(CodedInputStream stream, Set<String> operatorTypes) throws IOException {
        while (!stream.isAtEnd()) {
            var tag = stream.readTag();
            if (WireFormat.getTagFieldNumber(tag) == 0) return;
            switch (WireFormat.getTagFieldNumber(tag)) {
                case Onnx.AttributeProto.G_FIELD_NUMBER:
                case Onnx.AttributeProto.GRAPHS_FIELD_NUMBER:
                    parseDelimited(stream, s -> parseGraphOperatorTypes(s, operatorTypes));
                    break;
                default:
                    stream.skipField(tag);
                    break;
            }
        }
    }

    private static void parseGraphProto(CodedInputStream stream, Set<Path> externalDataPaths) throws IOException {
        while (!stream.isAtEnd()) {
            var tag = stream.readTag();
            if (WireFormat.getTagFieldNumber(tag) == 0) return;
            switch (WireFormat.getTagFieldNumber(tag)) {
                case Onnx.GraphProto.NODE_FIELD_NUMBER:
                    parseDelimited(stream, s -> parseNodeProto(s, externalDataPaths));
                    break;
                case Onnx.GraphProto.INITIALIZER_FIELD_NUMBER:
                    parseDelimited(stream, s -> parseTensorProto(s, externalDataPaths));
                    break;
                case Onnx.GraphProto.SPARSE_INITIALIZER_FIELD_NUMBER:
                    parseDelimited(stream, s -> parseSparseTensorProto(s, externalDataPaths));
                    break;
                default:
                    stream.skipField(tag);
                    break;
            }
        }
    }

    private static void parseFunctionProto(CodedInputStream stream, Set<Path> externalDataPaths) throws IOException {
        while (!stream.isAtEnd()) {
            var tag = stream.readTag();
            if (WireFormat.getTagFieldNumber(tag) == 0) return;
            if (WireFormat.getTagFieldNumber(tag) == Onnx.FunctionProto.NODE_FIELD_NUMBER) {
                parseDelimited(stream, s -> parseNodeProto(s, externalDataPaths));
            } else {
                stream.skipField(tag);
            }
        }
    }

    private static void parseNodeProto(CodedInputStream stream, Set<Path> externalDataPaths) throws IOException {
        while (!stream.isAtEnd()) {
            int tag = stream.readTag();
            if (WireFormat.getTagFieldNumber(tag) == 0) return;
            if (WireFormat.getTagFieldNumber(tag) == Onnx.NodeProto.ATTRIBUTE_FIELD_NUMBER) {
                parseDelimited(stream, s -> parseAttributeProto(s, externalDataPaths));
            } else {
                stream.skipField(tag);
            }
        }
    }

    // Finds external data locations in the tensors and subgraphs of an attribute.
    private static void parseAttributeProto(CodedInputStream stream, Set<Path> externalDataPaths) throws IOException {
        while (!stream.isAtEnd()) {
            var tag = stream.readTag();
            var fieldNumber = WireFormat.getTagFieldNumber(tag);
            if (fieldNumber == 0) break;

            switch (fieldNumber) {
                case Onnx.AttributeProto.T_FIELD_NUMBER:
                case Onnx.AttributeProto.TENSORS_FIELD_NUMBER:
                    parseDelimited(stream, s -> parseTensorProto(s, externalDataPaths));
                    break;
                case Onnx.AttributeProto.SPARSE_TENSOR_FIELD_NUMBER:
                case Onnx.AttributeProto.SPARSE_TENSORS_FIELD_NUMBER:
                    parseDelimited(stream, s -> parseSparseTensorProto(s, externalDataPaths));
                    break;
                case Onnx.AttributeProto.G_FIELD_NUMBER:
                case Onnx.AttributeProto.GRAPHS_FIELD_NUMBER:
                    parseDelimited(stream, s -> parseGraphProto(s, externalDataPaths));
                    break;
                default:
                    stream.skipField(tag);
                    break;
            }
        }
    }

    private static void parseTrainingInfoProto(CodedInputStream stream, Set<Path> externalDataPaths) throws IOException {
        while (!stream.isAtEnd()) {
            var tag = stream.readTag();
            if (WireFormat.getTagFieldNumber(tag) == 0) return;
            switch (WireFormat.getTagFieldNumber(tag)) {
                case Onnx.TrainingInfoProto.INITIALIZATION_FIELD_NUMBER:
                case Onnx.TrainingInfoProto.ALGORITHM_FIELD_NUMBER:
                    parseDelimited(stream, s -> parseGraphProto(s, externalDataPaths));
                    break;
                default:
                    stream.skipField(tag);
                    break;
            }
        }
    }

    private static void parseTensorProto(CodedInputStream stream, Set<Path> externalDataPaths) throws IOException {
        var dataLocation = Onnx.TensorProto.DataLocation.DEFAULT;
        String location = null;
        while (!stream.isAtEnd()) {
            int tag = stream.readTag();
            if (WireFormat.getTagFieldNumber(tag) == 0) break;
            switch (WireFormat.getTagFieldNumber(tag)) {
                case Onnx.TensorProto.EXTERNAL_DATA_FIELD_NUMBER:
                    var builder = Onnx.StringStringEntryProto.newBuilder();
                    stream.readMessage(builder, ExtensionRegistryLite.getEmptyRegistry());
                    var entry = builder.build();
                    if ("location".equals(entry.getKey())) location = entry.getValue();
                    break;
                case Onnx.TensorProto.DATA_LOCATION_FIELD_NUMBER:
                    dataLocation = Onnx.TensorProto.DataLocation.forNumber(stream.readEnum());
                    break;
                default:
                    stream.skipField(tag);
                    break;
            }
        }
        if (dataLocation == Onnx.TensorProto.DataLocation.EXTERNAL && location != null) {
            externalDataPaths.add(Paths.get(location));
        }
    }

    private static void parseSparseTensorProto(CodedInputStream stream, Set<Path> externalDataPaths) throws IOException {
        while (!stream.isAtEnd()) {
            var tag = stream.readTag();
            if (WireFormat.getTagFieldNumber(tag) == 0) return;
            switch (WireFormat.getTagFieldNumber(tag)) {
                case Onnx.SparseTensorProto.VALUES_FIELD_NUMBER:
                case Onnx.SparseTensorProto.INDICES_FIELD_NUMBER:
                    parseDelimited(stream, s -> parseTensorProto(s, externalDataPaths));
                    break;
                default:
                    stream.skipField(tag);
                    break;
            }
        }
    }

    private static void parseDelimited(CodedInputStream stream, CodedInputStreamConsumer consumer) throws IOException {
        var length = stream.readRawVarint32();
        var limit = stream.pushLimit(length);
        consumer.accept(stream);
        stream.popLimit(limit);
    }

    @FunctionalInterface
    private interface CodedInputStreamConsumer {
        void accept(CodedInputStream stream) throws IOException;
    }
}
