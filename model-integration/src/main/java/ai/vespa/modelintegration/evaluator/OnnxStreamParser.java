// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.modelintegration.evaluator;

import com.google.protobuf.CodedInputStream;
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
import java.util.HashSet;
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
                    parseDelimited(stream, s -> parseGraphProtoInitializers(s, externalDataPaths));
                    break;
                default:
                    stream.skipField(tag);
                    break;
            }
        }
    }

    private static void parseGraphProtoInitializers(CodedInputStream stream, Set<Path> externalDataPaths) throws IOException {
        while (!stream.isAtEnd()) {
            var tag = stream.readTag();
            if (WireFormat.getTagFieldNumber(tag) == 0) return;
            switch (WireFormat.getTagFieldNumber(tag)) {
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

    private static void parseTensorProto(CodedInputStream stream, Set<Path> externalDataPaths) throws IOException {
        var dataLocation = Onnx.TensorProto.DataLocation.DEFAULT;
        String location = null;
        while (!stream.isAtEnd()) {
            int tag = stream.readTag();
            if (WireFormat.getTagFieldNumber(tag) == 0) break;
            switch (WireFormat.getTagFieldNumber(tag)) {
                case Onnx.TensorProto.EXTERNAL_DATA_FIELD_NUMBER:
                    var builder = Onnx.StringStringEntryProto.newBuilder();
                    stream.readMessage(builder, com.google.protobuf.ExtensionRegistryLite.getEmptyRegistry());
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
            if (location.contains("..")) throw new IllegalArgumentException("External data path '" + location + "' must not contain '..'");
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
