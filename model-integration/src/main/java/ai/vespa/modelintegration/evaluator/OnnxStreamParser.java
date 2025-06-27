// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.modelintegration.evaluator;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.WireFormat;
import onnx.Onnx;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/**
 * Parses an ONNX model from a stream to extract information without loading the entire model into memory.
 *
 * @author bjorncs
 */
public class OnnxStreamParser {

    private OnnxStreamParser() {}

    /**
     * Finds all external data locations in an ONNX model by streaming the model file.
     *
     * @return set of relative file locations for the external data references in the provided ONNX model.
     */
    public static Set<Path> getExternalDataLocations(Path model) throws IOException {
        try (var bis = new BufferedInputStream(Files.newInputStream(model))) {
            var stream = CodedInputStream.newInstance(bis);
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

    private static void parseAttributeProto(CodedInputStream stream, Set<Path> externalDataPaths) throws IOException {
        Onnx.AttributeProto.AttributeType type = Onnx.AttributeProto.AttributeType.UNDEFINED;
        var tensorData = new ArrayList<byte[]>();
        var sparseTensorData = new ArrayList<byte[]>();

        while (!stream.isAtEnd()) {
            var tag = stream.readTag();
            var fieldNumber = WireFormat.getTagFieldNumber(tag);
            if (fieldNumber == 0) break;

            switch (fieldNumber) {
                case Onnx.AttributeProto.T_FIELD_NUMBER:
                case Onnx.AttributeProto.TENSORS_FIELD_NUMBER:
                    tensorData.add(readDelimitedBytes(stream));
                    break;
                case Onnx.AttributeProto.SPARSE_TENSOR_FIELD_NUMBER:
                case Onnx.AttributeProto.SPARSE_TENSORS_FIELD_NUMBER:
                    sparseTensorData.add(readDelimitedBytes(stream));
                    break;
                case Onnx.AttributeProto.TYPE_FIELD_NUMBER:
                    type = Onnx.AttributeProto.AttributeType.forNumber(stream.readEnum());
                    break;
                default:
                    stream.skipField(tag);
                    break;
            }
        }
        if (type == Onnx.AttributeProto.AttributeType.TENSOR || type == Onnx.AttributeProto.AttributeType.TENSORS) {
            for (var data : tensorData) {
                parseTensorProto(CodedInputStream.newInstance(data), externalDataPaths);
            }
        }
        if (type == Onnx.AttributeProto.AttributeType.SPARSE_TENSOR || type == Onnx.AttributeProto.AttributeType.SPARSE_TENSORS) {
            for (var data : sparseTensorData) {
                parseSparseTensorProto(CodedInputStream.newInstance(data), externalDataPaths);
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

    private static byte[] readDelimitedBytes(CodedInputStream stream) throws IOException {
        var length = stream.readRawVarint32();
        return stream.readRawBytes(length);
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
