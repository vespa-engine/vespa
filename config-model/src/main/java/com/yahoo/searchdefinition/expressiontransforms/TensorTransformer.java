// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.expressiontransforms;

import com.yahoo.searchdefinition.RankProfile;
import com.yahoo.searchdefinition.RankingConstant;
import com.yahoo.searchdefinition.document.Attribute;
import com.yahoo.searchlib.rankingexpression.Reference;
import com.yahoo.searchlib.rankingexpression.evaluation.Context;
import com.yahoo.searchlib.rankingexpression.evaluation.DoubleValue;
import com.yahoo.searchlib.rankingexpression.evaluation.MapContext;
import com.yahoo.searchlib.rankingexpression.evaluation.StringValue;
import com.yahoo.searchlib.rankingexpression.evaluation.TensorValue;
import com.yahoo.searchlib.rankingexpression.evaluation.Value;
import com.yahoo.searchlib.rankingexpression.rule.CompositeNode;
import com.yahoo.searchlib.rankingexpression.rule.ExpressionNode;
import com.yahoo.searchlib.rankingexpression.rule.FunctionNode;
import com.yahoo.searchlib.rankingexpression.rule.NameNode;
import com.yahoo.searchlib.rankingexpression.rule.ReferenceNode;
import com.yahoo.searchlib.rankingexpression.rule.TensorFunctionNode;
import com.yahoo.searchlib.rankingexpression.transform.ExpressionTransformer;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.evaluation.TypeContext;
import com.yahoo.tensor.functions.Reduce;

import java.util.List;
import java.util.Optional;

/**
 * Transforms and simplifies tensor expressions.
 *
 * Currently transforms min(tensor,dim) and max(tensor,dim) to
 * reduce(tensor,min/max,dim). This is necessary as the backend does
 * not recognize these forms of min and max.
 *
 * @author lesters
 */
public class TensorTransformer extends ExpressionTransformer<RankProfileTransformContext> {

    @Override
    public ExpressionNode transform(ExpressionNode node, RankProfileTransformContext context) {
        if (node instanceof CompositeNode) {
            node = transformChildren((CompositeNode) node, context);
        }
        if (node instanceof FunctionNode) {
            node = transformFunctionNode((FunctionNode) node, context);
        }
        return node;
    }

    private ExpressionNode transformFunctionNode(FunctionNode node, RankProfileTransformContext context) {
        switch (node.getFunction()) {
            case min:
            case max:
                return transformMaxAndMinFunctionNode(node, context);
        }
        return node;
    }

    /**
     * Transforms max and min functions if the first
     * argument returns a tensor type and the second argument is a valid
     * dimension in the tensor.
     */
    private ExpressionNode transformMaxAndMinFunctionNode(FunctionNode node, RankProfileTransformContext context) {
        if (node.children().size() != 2) {
            return node;
        }
        ExpressionNode arg1 = node.children().get(0);
        Optional<String> dimension = dimensionName(node.children().get(1));
        if (dimension.isPresent()) {
            TensorType type = arg1.type(context.rankProfile().typeContext(context.queryProfiles()));
            if (type.dimension(dimension.get()).isPresent()) {
                return replaceMaxAndMinFunction(node);
            }
        }
        return node;
    }

    private Optional<String> dimensionName(ExpressionNode node) {
        if (node instanceof ReferenceNode) {
            Reference reference = ((ReferenceNode)node).reference();
            if (reference.isIdentifier())
                return Optional.of(reference.name());
            else
                return Optional.empty();
        }
        else if (node instanceof NameNode) {
            return Optional.of(((NameNode)node).getValue());
        }
        else {
            return Optional.empty();
        }
    }

    private ExpressionNode replaceMaxAndMinFunction(FunctionNode node) {
        ExpressionNode arg1 = node.children().get(0);
        ExpressionNode arg2 = node.children().get(1);

        TensorFunctionNode.TensorFunctionExpressionNode expression = TensorFunctionNode.wrapArgument(arg1);
        Reduce.Aggregator aggregator = Reduce.Aggregator.valueOf(node.getFunction().name());
        String dimension = ((ReferenceNode) arg2).getName();

        return new TensorFunctionNode(new Reduce(expression, aggregator, dimension));
    }

}
