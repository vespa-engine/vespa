package com.yahoo.searchlib.rankingexpression.integration.ml.importer.tensorflow;

import com.yahoo.searchlib.rankingexpression.evaluation.BooleanValue;
import com.yahoo.searchlib.rankingexpression.evaluation.DoubleValue;
import com.yahoo.searchlib.rankingexpression.evaluation.TensorValue;
import com.yahoo.searchlib.rankingexpression.evaluation.Value;
import com.yahoo.searchlib.rankingexpression.integration.ml.importer.OrderedTensorType;
import com.yahoo.searchlib.rankingexpression.integration.ml.importer.operations.IntermediateOperation;
import org.tensorflow.framework.AttrValue;
import org.tensorflow.framework.NodeDef;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Converts TensorFlow node attributes to Vespa attribute values.
 *
 * @author lesters
 */
public class AttributeConverter implements IntermediateOperation.AttributeMap {

    private final Map<String, AttrValue> attributeMap;

    public AttributeConverter(NodeDef node) {
        attributeMap = node.getAttrMap();
    }

    public static AttributeConverter convert(NodeDef node) {
        return new AttributeConverter(node);
    }

    @Override
    public Optional<Value> get(String key) {
        if (attributeMap.containsKey(key)) {
            AttrValue attrValue = attributeMap.get(key);
            if (attrValue.getValueCase() == AttrValue.ValueCase.TENSOR) {
                return Optional.empty();  // requires type
            }
            if (attrValue.getValueCase() == AttrValue.ValueCase.B) {
                return Optional.of(new BooleanValue(attrValue.getB()));
            }
            if (attrValue.getValueCase() == AttrValue.ValueCase.I) {
                return Optional.of(new DoubleValue(attrValue.getI()));
            }
            if (attrValue.getValueCase() == AttrValue.ValueCase.F) {
                return Optional.of(new DoubleValue(attrValue.getF()));
            }
        }
        return Optional.empty();
    }

    @Override
    public Optional<Value> get(String key, OrderedTensorType type) {
        if (attributeMap.containsKey(key)) {
            AttrValue attrValue = attributeMap.get(key);
            if (attrValue.getValueCase() == AttrValue.ValueCase.TENSOR) {
                return Optional.of(new TensorValue(TensorConverter.toVespaTensor(attrValue.getTensor(), type.type())));
            }
        }
        return get(key);
    }

    @Override
    public Optional<List<Value>> getList(String key) {
        if (attributeMap.containsKey(key)) {
            AttrValue attrValue = attributeMap.get(key);
            if (attrValue.getValueCase() == AttrValue.ValueCase.LIST) {
                AttrValue.ListValue listValue = attrValue.getList();
                if ( ! listValue.getBList().isEmpty()) {
                    return Optional.of(listValue.getBList().stream().map(BooleanValue::new).collect(Collectors.toList()));
                }
                if ( ! listValue.getIList().isEmpty()) {
                    return Optional.of(listValue.getIList().stream().map(DoubleValue::new).collect(Collectors.toList()));
                }
                if ( ! listValue.getFList().isEmpty()) {
                    return Optional.of(listValue.getFList().stream().map(DoubleValue::new).collect(Collectors.toList()));
                }
                // add the rest
            }
        }
        return Optional.empty();
    }
}
