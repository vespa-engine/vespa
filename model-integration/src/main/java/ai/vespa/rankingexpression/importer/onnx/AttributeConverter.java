// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.rankingexpression.importer.onnx;

import ai.vespa.rankingexpression.importer.OrderedTensorType;
import ai.vespa.rankingexpression.importer.operations.IntermediateOperation;
import com.yahoo.searchlib.rankingexpression.evaluation.DoubleValue;
import com.yahoo.searchlib.rankingexpression.evaluation.StringValue;
import com.yahoo.searchlib.rankingexpression.evaluation.TensorValue;
import com.yahoo.searchlib.rankingexpression.evaluation.Value;
import onnx.Onnx;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Converts Onnx node attributes to Vespa attribute values.
 *
 * @author lesters
 */
class AttributeConverter implements IntermediateOperation.AttributeMap {

    private final Onnx.NodeProto node;

    private AttributeConverter(Onnx.NodeProto node) {
        this.node = node;
    }

    static AttributeConverter convert(Onnx.NodeProto node) {
        return new AttributeConverter(node);
    }

    @Override
    public Optional<Value> get(String name) {
        for (Onnx.AttributeProto attr : node.getAttributeList()) {
            if (attr.getName().equals(name)) {
                switch (attr.getType()) {
                    case INT: return Optional.of(DoubleValue.frozen(attr.getI()));
                    case FLOAT: return Optional.of(DoubleValue.frozen(attr.getF()));
                    case STRING: return Optional.of(StringValue.frozen(attr.getS().toString()));
                    case TENSOR: return Optional.of(new TensorValue(TensorConverter.toVespaTensor(attr.getT(), TypeConverter.typeFrom(attr.getT()))));
                    default:
                        return Optional.empty();
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public Optional<Value> get(String name, OrderedTensorType type) {
        return Optional.empty();
    }

    @Override
    public Optional<List<Value>> getList(String name) {
        for (Onnx.AttributeProto attr : node.getAttributeList()) {
            if (attr.getName().equals(name)) {
                switch (attr.getType()) {
                    case INTS: return Optional.of(attr.getIntsList().stream().map(DoubleValue::new).collect(Collectors.toList()));
                    case FLOATS: return Optional.of(attr.getFloatsList().stream().map(DoubleValue::new).collect(Collectors.toList()));
                    case STRINGS: return Optional.of(attr.getStringsList().stream().map((s) -> StringValue.frozen(s.toString())).collect(Collectors.toList()));
                    default:
                        return Optional.empty();
                }
            }
        }
        return Optional.empty();
    }

}
