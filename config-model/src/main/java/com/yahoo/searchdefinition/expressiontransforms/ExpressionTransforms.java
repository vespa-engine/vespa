package com.yahoo.searchdefinition.expressiontransforms;

import com.google.common.collect.ImmutableList;
import com.yahoo.searchlib.rankingexpression.RankingExpression;
import com.yahoo.searchlib.rankingexpression.transform.ConstantDereferencer;
import com.yahoo.searchlib.rankingexpression.transform.ExpressionTransformer;
import com.yahoo.searchlib.rankingexpression.transform.Simplifier;

import java.util.List;

/**
 * The transformations done on ranking expressions done at config time before passing them on to the Vespa
 * engine for execution.
 *
 * An instance of this class has scope of one complete deployment.
 *
 * @author bratseth
 */
public class ExpressionTransforms {

    private final List<ExpressionTransformer> transforms =
            ImmutableList.of(new TensorFlowFeatureConverter(),
                             new ConstantDereferencer(),
                             new ConstantTensorTransformer(),
                             new MacroInliner(),
                             new MacroShadower(),
                             new TensorTransformer(),
                             new Simplifier());

    public RankingExpression transform(RankingExpression expression, RankProfileTransformContext context) {
        for (ExpressionTransformer transformer : transforms)
            expression = transformer.transform(expression, context);
        return expression;
    }

}
