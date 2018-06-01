// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression.integration.ml.importer.operations;

import com.yahoo.searchlib.rankingexpression.Reference;
import com.yahoo.searchlib.rankingexpression.evaluation.Value;
import com.yahoo.searchlib.rankingexpression.integration.ml.importer.DimensionRenamer;
import com.yahoo.searchlib.rankingexpression.integration.ml.importer.OrderedTensorType;
import com.yahoo.searchlib.rankingexpression.rule.ConstantNode;
import com.yahoo.searchlib.rankingexpression.rule.ExpressionNode;
import com.yahoo.searchlib.rankingexpression.rule.ReferenceNode;
import com.yahoo.searchlib.rankingexpression.rule.TensorFunctionNode;
import com.yahoo.tensor.TensorType;
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
    public Optional<TensorFunction> function() {
        if (function == null) {
            function = lazyGetFunction();
        }
        return Optional.ofNullable(function);
    }

    @Override
    protected TensorFunction lazyGetFunction() {
        ExpressionNode expressionNode;
        if (type.type().rank() == 0 && getConstantValue().isPresent()) {
            expressionNode = new ConstantNode(getConstantValue().get().asDoubleValue());
        } else {
            expressionNode = new ReferenceNode(Reference.simple("constant", vespaName()));
        }
        return new TensorFunctionNode.TensorFunctionExpressionNode(expressionNode);
    }

    /** Constant names are prefixed by "modelName_" to avoid name conflicts between models */
    @Override
    public String vespaName() {
        return modelName + "_" + super.vespaName();
    }

    @Override
    public void addDimensionNameConstraints(DimensionRenamer renamer) {
        for (TensorType.Dimension dimension : type.type().dimensions()) {
            renamer.addDimension(dimension.name());
        }
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
        if ( ! value.isPresent()) {
            throw new IllegalArgumentException("Node '" + name + "' of type " +
                                               "const has missing or non-recognized 'value' attribute");
        }
        return value.get();
    }
}
