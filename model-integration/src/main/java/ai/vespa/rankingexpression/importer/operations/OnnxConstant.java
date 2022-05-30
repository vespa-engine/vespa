// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.rankingexpression.importer.operations;

import ai.vespa.rankingexpression.importer.DimensionRenamer;
import ai.vespa.rankingexpression.importer.OrderedTensorType;
import com.yahoo.searchlib.rankingexpression.Reference;
import com.yahoo.searchlib.rankingexpression.evaluation.TensorValue;
import com.yahoo.searchlib.rankingexpression.evaluation.Value;
import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.functions.TensorFunction;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class OnnxConstant extends IntermediateOperation {

    private final AttributeMap attributeMap;
    private final Value value;

    public OnnxConstant(String modelName, String nodeName, List<IntermediateOperation> inputs, AttributeMap attributeMap) {
        super(modelName, nodeName, inputs);
        this.attributeMap = attributeMap;
        this.value = value();
        setConstantValueFunction(type -> new TensorValue(this.value.asTensor()));
    }

    @Override
    protected OrderedTensorType lazyGetType() {
        OrderedTensorType type;
        if (value instanceof TensorValue) {
            type = OrderedTensorType.fromSpec(value.type().toString()).rename(vespaName() + "_");
        } else {
            type = OrderedTensorType.fromDimensionList(TensorType.Value.DOUBLE, Collections.emptyList());
        }
        return type;
    }

    @Override
    protected TensorFunction<Reference> lazyGetFunction() {
        return null;  // will be added by function() since this is constant.
    }

    @Override
    public Optional<Value> getConstantValue() {
        return Optional.of(new TensorValue(value.asTensor().withType(type().get().type())));
    }

    @Override
    public void addDimensionNameConstraints(DimensionRenamer renamer) {
        addConstraintsFrom(type, renamer);
    }

    @Override
    public boolean isConstant() {
        return true;
    }

    @Override
    public OnnxConstant withInputs(List<IntermediateOperation> inputs) {
        return new OnnxConstant(modelName(), name(), inputs, attributeMap);
    }

    @Override
    public String operationName() { return "Constant"; }

    @Override
    public String toString() {
        return "Constant(" + type + ")";
    }

    @Override
    public String toFullString() {
        return "\t" + type + ":\tConstant(" + type + ")";
    }

    private Value value() {
        Optional<Value> value = attributeMap.get("value");
        if (value.isEmpty()) {
            value = attributeMap.get("value_float");
            if (value.isEmpty()) {
                value = attributeMap.get("value_int");
            }
        }
        if (value.isEmpty()) {
            throw new IllegalArgumentException("Node '" + name + "' of type " +
                                               "constant has missing or non-supported 'value' attribute");
        }
        return value.get();
    }

}
