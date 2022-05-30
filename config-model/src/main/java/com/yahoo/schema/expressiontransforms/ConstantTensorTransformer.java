// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.expressiontransforms;

import com.yahoo.schema.FeatureNames;
import com.yahoo.searchlib.rankingexpression.Reference;
import com.yahoo.searchlib.rankingexpression.evaluation.TensorValue;
import com.yahoo.searchlib.rankingexpression.evaluation.Value;
import com.yahoo.searchlib.rankingexpression.rule.CompositeNode;
import com.yahoo.searchlib.rankingexpression.rule.ExpressionNode;
import com.yahoo.searchlib.rankingexpression.rule.ReferenceNode;
import com.yahoo.searchlib.rankingexpression.transform.ExpressionTransformer;

import java.util.ArrayList;
import java.util.List;

/**
 * Transforms named references to constant tensors with the rank feature 'constant'.
 *
 * @author geirst
 */
public class ConstantTensorTransformer extends ExpressionTransformer<RankProfileTransformContext> {

    @Override
    public ExpressionNode transform(ExpressionNode node, RankProfileTransformContext context) {
        if (node instanceof ReferenceNode) {
            return transformFeature((ReferenceNode) node, context);
        } else if (node instanceof CompositeNode) {
            return transformChildren((CompositeNode) node, context);
        } else {
            return node;
        }
    }

    private ExpressionNode transformFeature(ReferenceNode node, RankProfileTransformContext context) {
        if ( ! node.getArguments().isEmpty() && ! FeatureNames.isSimpleFeature(node.reference())) {
            return transformArguments(node, context);
        } else {
            return transformConstantReference(node, context);
        }
    }

    private ExpressionNode transformArguments(ReferenceNode node, RankProfileTransformContext context) {
        List<ExpressionNode> arguments = node.getArguments().expressions();
        List<ExpressionNode> transformedArguments = new ArrayList<>(arguments.size());
        for (ExpressionNode argument : arguments) {
            transformedArguments.add(transform(argument, context));
        }
        return node.setArguments(transformedArguments);
    }

    private ExpressionNode transformConstantReference(ReferenceNode node, RankProfileTransformContext context) {
        String constantName = node.getName();
        Reference constantReference = node.reference();
        if (FeatureNames.isConstantFeature(constantReference)) {
            constantName = constantReference.simpleArgument().orElse(null);
        } else if (constantReference.isIdentifier()) {
            constantReference = FeatureNames.asConstantFeature(constantName);
        } else {
            return node;
        }
        Value value = context.constants().get(constantName);
        if (value == null || value.type().rank() == 0) return node;

        TensorValue tensorValue = (TensorValue)value;
        String tensorType = tensorValue.asTensor().type().toString();
        context.rankProperties().put(constantReference + ".value", tensorValue.toString());
        context.rankProperties().put(constantReference + ".type", tensorType);
        return new ReferenceNode(constantReference);
    }

}
