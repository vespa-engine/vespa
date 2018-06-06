// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression.integration.tensorflow.importer.operations;

import com.yahoo.searchlib.rankingexpression.Reference;
import com.yahoo.searchlib.rankingexpression.evaluation.BooleanValue;
import com.yahoo.searchlib.rankingexpression.evaluation.DoubleValue;
import com.yahoo.searchlib.rankingexpression.evaluation.TensorValue;
import com.yahoo.searchlib.rankingexpression.evaluation.Value;
import com.yahoo.searchlib.rankingexpression.integration.tensorflow.importer.DimensionRenamer;
import com.yahoo.searchlib.rankingexpression.integration.tensorflow.importer.OrderedTensorType;
import com.yahoo.searchlib.rankingexpression.integration.tensorflow.importer.TensorConverter;
import com.yahoo.searchlib.rankingexpression.rule.ConstantNode;
import com.yahoo.searchlib.rankingexpression.rule.ExpressionNode;
import com.yahoo.searchlib.rankingexpression.rule.ReferenceNode;
import com.yahoo.searchlib.rankingexpression.rule.TensorFunctionNode;
import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.functions.TensorFunction;
import org.tensorflow.framework.AttrValue;
import org.tensorflow.framework.NodeDef;

import java.util.List;
import java.util.Optional;

public class Const extends TensorFlowOperation {

    public Const(String modelName, NodeDef node, List<TensorFlowOperation> inputs, int port) {
        super(modelName, node, inputs, port);
        setConstantValue(value());
    }

    @Override
    protected OrderedTensorType lazyGetType() {
        return OrderedTensorType.fromTensorFlowType(node, vespaName() + "_");
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
        return modelName() + "_" + super.vespaName();
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
        if ( ! node.getAttrMap().containsKey("value")) {
            throw new IllegalArgumentException("Node '" + node.getName() + "' of type " +
                                               "const has missing 'value' attribute");
        }
        AttrValue attrValue = node.getAttrMap().get("value");
        if (attrValue.getValueCase() == AttrValue.ValueCase.TENSOR) {
            return new TensorValue(TensorConverter.toVespaTensor(attrValue.getTensor(), type().get().type()));
        }
        if (attrValue.getValueCase() == AttrValue.ValueCase.B) {
            return new BooleanValue(attrValue.getB());
        }
        if (attrValue.getValueCase() == AttrValue.ValueCase.I) {
            return new DoubleValue(attrValue.getI());
        }
        if (attrValue.getValueCase() == AttrValue.ValueCase.F) {
            return new DoubleValue(attrValue.getF());
        }
        throw new IllegalArgumentException("Requesting value of constant in " +
                                           node.getName() + " but type is not recognized.");
    }
}
