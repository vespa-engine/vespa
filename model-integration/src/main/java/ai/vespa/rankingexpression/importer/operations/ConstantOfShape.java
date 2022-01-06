// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.rankingexpression.importer.operations;

import ai.vespa.rankingexpression.importer.DimensionRenamer;
import ai.vespa.rankingexpression.importer.OrderedTensorType;
import com.yahoo.searchlib.rankingexpression.Reference;
import com.yahoo.searchlib.rankingexpression.evaluation.DoubleValue;
import com.yahoo.searchlib.rankingexpression.evaluation.Value;
import com.yahoo.searchlib.rankingexpression.rule.ConstantNode;
import com.yahoo.searchlib.rankingexpression.rule.ExpressionNode;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.functions.Generate;
import com.yahoo.tensor.functions.TensorFunction;

import java.util.Iterator;
import java.util.List;
import java.util.Optional;

import static com.yahoo.searchlib.rankingexpression.rule.TensorFunctionNode.wrapScalar;

public class ConstantOfShape extends IntermediateOperation {

    private final AttributeMap attributeMap;

    private TensorType.Value valueTypeOfTensor = TensorType.Value.DOUBLE;
    private double           valueToFillWith = 0.0;


    public ConstantOfShape(String modelName, String nodeName, List<IntermediateOperation> inputs, AttributeMap attributeMap) {
        super(modelName, nodeName, inputs);
        this.attributeMap = attributeMap;

        Optional<Value> value = attributeMap.get("value");
        if (value.isPresent()) {
            Tensor t = value.get().asTensor();
            valueTypeOfTensor = t.type().valueType();
            valueToFillWith = t.valueIterator().next();
        }
    }

    @Override
    protected OrderedTensorType lazyGetType() {
        if ( ! allInputTypesPresent(1)) return null;

        IntermediateOperation input = inputs.get(0);
        if (input.getConstantValue().isEmpty()) {
            throw new IllegalArgumentException("ConstantOfShape: 'shape' input must be a constant.");
        }
        Tensor shape = input.getConstantValue().get().asTensor();
        if (shape.type().dimensions().size() > 1) {
            throw new IllegalArgumentException("ConstantOfShape: 'shape' input must be a tensor with 0 or 1 dimensions.");
        }

        OrderedTensorType.Builder builder = new OrderedTensorType.Builder(valueTypeOfTensor);
        Iterator<Double> iter = shape.valueIterator();
        for (int i = 0; iter.hasNext(); i++) {
            builder.add(TensorType.Dimension.indexed(vespaName() + "_" + i, iter.next().longValue()));
        }
        return builder.build();
    }

    @Override
    protected TensorFunction<Reference> lazyGetFunction() {
        if ( ! allInputTypesPresent(1)) return null;
        ExpressionNode valueExpr = new ConstantNode(new DoubleValue(valueToFillWith));
        TensorFunction<Reference> function = Generate.bound(type.type(), wrapScalar(valueExpr));
        return function;
    }

    @Override
    public void addDimensionNameConstraints(DimensionRenamer renamer) {
        addConstraintsFrom(type, renamer);
    }

    @Override
    public ConstantOfShape withInputs(List<IntermediateOperation> inputs) {
        return new ConstantOfShape(modelName(), name(), inputs, attributeMap);
    }

    @Override
    public String operationName() { return "ConstantOfShape"; }

}
