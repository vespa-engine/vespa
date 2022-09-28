// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.rankingexpression.importer.operations;

import ai.vespa.rankingexpression.importer.DimensionRenamer;
import ai.vespa.rankingexpression.importer.OrderedTensorType;
import com.yahoo.searchlib.rankingexpression.Reference;
import com.yahoo.searchlib.rankingexpression.evaluation.DoubleValue;
import com.yahoo.searchlib.rankingexpression.rule.OperationNode;
import com.yahoo.searchlib.rankingexpression.rule.Operator;
import com.yahoo.searchlib.rankingexpression.rule.ConstantNode;
import com.yahoo.searchlib.rankingexpression.rule.EmbracedNode;
import com.yahoo.searchlib.rankingexpression.rule.ExpressionNode;
import com.yahoo.searchlib.rankingexpression.rule.ReferenceNode;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.functions.Generate;
import com.yahoo.tensor.functions.TensorFunction;

import java.util.List;

import static com.yahoo.searchlib.rankingexpression.rule.TensorFunctionNode.wrapScalar;

public class Range extends IntermediateOperation {

    private double start;
    private double limit;
    private double delta;
    private long elements;

    public Range(String modelName, String nodeName, List<IntermediateOperation> inputs) {
        super(modelName, nodeName, inputs);
    }

    private double getConstantInput(int index, String name) {
        IntermediateOperation input = inputs.get(index);
        if (input.getConstantValue().isEmpty()) {
            throw new IllegalArgumentException("Range: " + name + " input must be a constant.");
        }
        Tensor value = input.getConstantValue().get().asTensor();
        if ( ! input.getConstantValue().get().hasDouble()) {
            throw new IllegalArgumentException("Range: " + name + " input must be a scalar.");
        }
        return value.asDouble();
    }

    @Override
    protected OrderedTensorType lazyGetType() {
        if ( ! allInputTypesPresent(3)) return null;

        start = getConstantInput(0, "start");  // must be constant because we need to know type
        limit = getConstantInput(1, "limit");
        delta = getConstantInput(2, "delta");
        elements = (long) Math.ceil((limit - start) / delta);

        OrderedTensorType type = new OrderedTensorType.Builder(inputs.get(0).type().get().type().valueType())
                .add(TensorType.Dimension.indexed(vespaName(), elements))
                .build();
        return type;
    }

    @Override
    protected TensorFunction<Reference> lazyGetFunction() {
        if ( ! allInputTypesPresent(3)) return null;
        String dimensionName = type().get().dimensionNames().get(0);
        ExpressionNode startExpr = new ConstantNode(new DoubleValue(start));
        ExpressionNode deltaExpr = new ConstantNode(new DoubleValue(delta));
        ExpressionNode dimExpr = new EmbracedNode(new ReferenceNode(dimensionName));
        ExpressionNode stepExpr = new OperationNode(deltaExpr, Operator.multiply, dimExpr);
        ExpressionNode addExpr = new OperationNode(startExpr, Operator.plus, stepExpr);
        TensorFunction<Reference> function = Generate.bound(type.type(), wrapScalar(addExpr));
        return function;
    }

    @Override
    public void addDimensionNameConstraints(DimensionRenamer renamer) {
        addConstraintsFrom(type, renamer);
    }

    @Override
    public Range withInputs(List<IntermediateOperation> inputs) {
        return new Range(modelName(), name(), inputs);
    }

    @Override
    public String operationName() { return "Range"; }

}
