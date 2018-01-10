package com.yahoo.searchdefinition.expressiontransforms;

import com.google.common.collect.ImmutableList;
import com.yahoo.searchdefinition.RankProfile;
import com.yahoo.searchlib.rankingexpression.RankingExpression;
import com.yahoo.searchlib.rankingexpression.evaluation.Value;
import com.yahoo.searchlib.rankingexpression.transform.ConstantDereferencer;
import com.yahoo.searchlib.rankingexpression.transform.ExpressionTransformer;
import com.yahoo.searchlib.rankingexpression.transform.Simplifier;
import com.yahoo.searchlib.rankingexpression.transform.TransformContext;

import java.util.List;
import java.util.Map;

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

    public RankingExpression transform(RankingExpression expression,
                                       RankProfile rankProfile,
                                       Map<String, Value> constants,
                                       Map<String, RankProfile.Macro> inlineMacros,
                                       Map<String, String> rankPropertiesOutput) {
        TransformContext context = new RankProfileTransformContext(rankProfile, constants, inlineMacros, rankPropertiesOutput);
        for (ExpressionTransformer transformer : transforms)
            expression = transformer.transform(expression, context);
        return expression;
    }

}
