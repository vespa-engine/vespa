// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.expressiontransforms;

import com.google.common.collect.ImmutableList;
import com.yahoo.searchlib.rankingexpression.RankingExpression;
import com.yahoo.searchlib.rankingexpression.transform.ConstantDereferencer;
import com.yahoo.searchlib.rankingexpression.transform.ExpressionTransformer;
import com.yahoo.searchlib.rankingexpression.transform.Simplifier;
import com.yahoo.searchlib.rankingexpression.transform.TensorMaxMinTransformer;

import java.util.List;

/**
 * The transformations done on ranking expressions done at config time before passing them on to the Vespa
 * engine for execution.
 *
 * An instance of this class has scope of a compilation of a single rank profile.
 *
 * @author bratseth
 */
public class ExpressionTransforms {

    private final List<ExpressionTransformer> transforms;

    public ExpressionTransforms() {
        transforms =
                ImmutableList.of(new TensorFlowFeatureConverter(),
                                 new OnnxFeatureConverter(),
                                 new OnnxModelTransformer(),
                                 new XgboostFeatureConverter(),
                                 new LightGBMFeatureConverter(),
                                 new TokenTransformer(),
                                 new ConstantDereferencer(),
                                 new ConstantTensorTransformer(),
                                 new FunctionInliner(),
                                 new FunctionShadower(),
                                 new TensorMaxMinTransformer(),
                                 new Simplifier());
    }

    public RankingExpression transform(RankingExpression expression, RankProfileTransformContext context) {
        for (ExpressionTransformer transformer : transforms)
            expression = transformer.transform(expression, context);
        return expression;
    }

}
