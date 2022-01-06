// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.rankingexpression.importer.operations;

import com.yahoo.searchlib.rankingexpression.Reference;
import com.yahoo.searchlib.rankingexpression.evaluation.Value;
import ai.vespa.rankingexpression.importer.DimensionRenamer;
import ai.vespa.rankingexpression.importer.OrderedTensorType;
import com.yahoo.searchlib.rankingexpression.rule.ConstantNode;
import com.yahoo.searchlib.rankingexpression.rule.ExpressionNode;
import com.yahoo.searchlib.rankingexpression.rule.ReferenceNode;
import com.yahoo.searchlib.rankingexpression.rule.TensorFunctionNode;
import com.yahoo.tensor.functions.TensorFunction;

import java.util.List;
import java.util.Optional;

public class Const extends IntermediateOperation {

    private final AttributeMap attributeMap;

    public Const(String modelName,
                 String nodeName,
                 List<IntermediateOperation> inputs,
                 AttributeMap attributeMap,
                 OrderedTensorType type) {
        super(modelName, nodeName, inputs);
        this.attributeMap = attributeMap;
        this.type = type.rename(vespaName() + "_");
        setConstantValue(value());
    }

    @Override
    protected OrderedTensorType lazyGetType() {
        return type;
    }

    @Override
    public Optional<TensorFunction<Reference>> function() {
        if (function == null) {
            function = lazyGetFunction();
        }
        return Optional.ofNullable(function);
    }

    @Override
    protected TensorFunction<Reference> lazyGetFunction() {
        ExpressionNode expressionNode;
        if (type.type().rank() == 0 && getConstantValue().isPresent()) {
            expressionNode = new ConstantNode(getConstantValue().get().asDoubleValue());
        } else {
            expressionNode = new ReferenceNode(Reference.simple("constant", vespaName()));
        }
        return new TensorFunctionNode.ExpressionTensorFunction(expressionNode);
    }

    @Override
    public void addDimensionNameConstraints(DimensionRenamer renamer) {
        addConstraintsFrom(type, renamer);
    }

    @Override
    public void renameDimensions(DimensionRenamer renamer) {
        super.renameDimensions(renamer);
        setConstantValue(value());
    }

    @Override
    public boolean isConstant() {
        return true;
    }

    private Value value() {
        Optional<Value> value = attributeMap.get("value", type);
        if (value.isEmpty()) {
            throw new IllegalArgumentException("Node '" + name + "' of type " +
                                               "const has missing or non-recognized 'value' attribute");
        }
        return value.get();
    }

    @Override
    public Const withInputs(List<IntermediateOperation> inputs) {
        return new Const(modelName(), name(), inputs, attributeMap, type);
    }

    @Override
    public String operationName() { return "Const"; }

    @Override
    public String toString() {
        return "Const(" + type + ")";
    }

    @Override
    public String toFullString() {
        return "\t" + type + ":\tConst(" + getConstantValue().get() + ")";
    }

}
