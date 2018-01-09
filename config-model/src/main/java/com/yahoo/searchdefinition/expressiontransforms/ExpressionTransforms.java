package com.yahoo.searchdefinition.expressiontransforms;

import com.yahoo.searchdefinition.RankProfile;
import com.yahoo.searchlib.rankingexpression.RankingExpression;
import com.yahoo.searchlib.rankingexpression.evaluation.Value;
import com.yahoo.searchlib.rankingexpression.transform.ConstantDereferencer;
import com.yahoo.searchlib.rankingexpression.transform.Simplifier;

import java.util.Map;

/**
 * The transformations done on ranking expressions done at config time before passing them on to the Vespa
 * engine for execution.
 *
 * @author bratseth
 */
public class ExpressionTransforms {

    public RankingExpression transform(RankingExpression expression,
                                       RankProfile rankProfile,
                                       Map<String, Value> constants,
                                       Map<String, RankProfile.Macro> inlineMacros,
                                       Map<String, String> rankPropertiesOutput) {
        expression = new TensorFlowFeatureConverter(rankProfile).transform(expression);
        expression = new ConstantDereferencer(constants).transform(expression);
        expression = new ConstantTensorTransformer(constants, rankPropertiesOutput).transform(expression);
        expression = new MacroInliner(inlineMacros).transform(expression);
        expression = new MacroShadower(rankProfile.getMacros()).transform(expression);
        expression = new TensorTransformer(rankProfile).transform(expression);
        expression = new Simplifier().transform(expression);
        return expression;
    }

}
