// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.expressiontransforms;

import com.yahoo.schema.FeatureNames;
import com.yahoo.searchlib.rankingexpression.RankingExpression;
import com.yahoo.searchlib.rankingexpression.Reference;
import com.yahoo.searchlib.rankingexpression.evaluation.TensorValue;
import com.yahoo.searchlib.rankingexpression.evaluation.Value;
import com.yahoo.searchlib.rankingexpression.parser.ParseException;
import com.yahoo.searchlib.rankingexpression.rule.CompositeNode;
import com.yahoo.searchlib.rankingexpression.rule.ExpressionNode;
import com.yahoo.searchlib.rankingexpression.rule.ReferenceNode;
import com.yahoo.searchlib.rankingexpression.rule.TensorFunctionNode;
import com.yahoo.searchlib.rankingexpression.transform.ExpressionTransformer;

import java.io.StringReader;
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
        if (node instanceof TensorFunctionNode tfn) {
            node = tfn.withTransformedExpressions(expr -> transform(expr, context));
        }
        if (node instanceof ReferenceNode) {
            return transformFeature((ReferenceNode) node, context);
        } else if (node instanceof CompositeNode) {
            return transformChildren((CompositeNode) node, context);
        } else {
            return node;
        }
    }

    private ExpressionNode transformFeature(ReferenceNode node, RankProfileTransformContext context) {
        Reference ref = node.reference();
        String name = ref.name();
        var args = ref.arguments();
        if (name.equals("onnx") && args.size() == 1) {
            var arg = args.expressions().get(0);
            var models = context.rankProfile().onnxModels();
            var model = models.get(arg.toString());
            if (model != null) {
                for (var entry : model.getInputMap().entrySet()) {
                    String source = entry.getValue();
                    var reader = new StringReader(source);
                    try {
                        var asExpression = new RankingExpression(reader);
                        String transformed = transform(asExpression.getRoot(), context).toString();
                        if (! source.equals(transformed)) {
                            // not sure about this:
                            throw new IllegalStateException("unexpected rewrite: " + source + " => " + transformed + " for onnx input " + entry.getKey());
                            // consider instead: model.addInputNameMapping(entry.getKey(), transformed, true);
                        }
                    } catch (ParseException e) {
                        throw new IllegalArgumentException("illegal onnx input '" + source + "': " + e.getMessage());
                    }
                }
                return node;
            }
        }
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
