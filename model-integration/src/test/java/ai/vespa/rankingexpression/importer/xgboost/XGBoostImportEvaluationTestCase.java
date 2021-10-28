// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.rankingexpression.importer.xgboost;

import com.yahoo.searchlib.rankingexpression.RankingExpression;
import com.yahoo.searchlib.rankingexpression.evaluation.ArrayContext;
import com.yahoo.searchlib.rankingexpression.evaluation.Context;
import com.yahoo.searchlib.rankingexpression.evaluation.ContextIndex;
import com.yahoo.searchlib.rankingexpression.evaluation.DoubleValue;
import com.yahoo.searchlib.rankingexpression.evaluation.ExpressionOptimizer;
import com.yahoo.searchlib.rankingexpression.evaluation.TensorValue;
import com.yahoo.searchlib.rankingexpression.evaluation.gbdtoptimization.GBDTForestNode;
import com.yahoo.tensor.Tensor;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author lesters
 */
public class XGBoostImportEvaluationTestCase {

    @Test
    public void testXGBoostEvaluation() {
        RankingExpression expression = new XGBoostImporter()
                .importModel("xgb", "src/test/models/xgboost/xgboost.test.json")
                .expressions().get("xgb");

        ArrayContext context = new ArrayContext(expression, DoubleValue.NaN);

        assertXGBoostEvaluation(1.0, expression, features(context, "f1", 0.0, "f2", 0.0));
        assertXGBoostEvaluation(2.0, expression, features(context, "f1", 0.0, "f2", 1.0));
        assertXGBoostEvaluation(3.0, expression, features(context, "f1", 1.0, "f2", 0.0));
        assertXGBoostEvaluation(4.0, expression, features(context, "f1", 1.0, "f2", 1.0));
        assertXGBoostEvaluation(5.0, expression, features(context, "f1", 0.0));
        assertXGBoostEvaluation(6.0, expression, features(context, "f1", 1.0));
        assertXGBoostEvaluation(7.0, expression, features(context, "f2", 0.0));
        assertXGBoostEvaluation(9.0, expression, features(context, "f2", 1.0));
        assertXGBoostEvaluation(11.0, expression, features(context));
        assertXGBoostEvaluation(5.0, expression, features(context, "f1", Tensor.from(0.0)));
        assertXGBoostEvaluation(6.0, expression, features(context, "f1", Tensor.from(1.0)));

        ExpressionOptimizer optimizer = new ExpressionOptimizer();
        optimizer.optimize(expression, (ContextIndex)context);
        assertTrue(expression.getRoot() instanceof GBDTForestNode);

        assertXGBoostEvaluation(1.0, expression, features(context, "f1", 0.0, "f2", 0.0));
        assertXGBoostEvaluation(2.0, expression, features(context, "f1", 0.0, "f2", 1.0));
        assertXGBoostEvaluation(3.0, expression, features(context, "f1", 1.0, "f2", 0.0));
        assertXGBoostEvaluation(4.0, expression, features(context, "f1", 1.0, "f2", 1.0));
        assertXGBoostEvaluation(5.0, expression, features(context, "f1", 0.0));
        assertXGBoostEvaluation(6.0, expression, features(context, "f1", 1.0));
        assertXGBoostEvaluation(7.0, expression, features(context, "f2", 0.0));
        assertXGBoostEvaluation(9.0, expression, features(context, "f2", 1.0));
        assertXGBoostEvaluation(11.0, expression, features(context));
        assertXGBoostEvaluation(5.0, expression, features(context, "f1", Tensor.from(0.0)));
        assertXGBoostEvaluation(6.0, expression, features(context, "f1", Tensor.from(1.0)));
    }

    private ArrayContext features(ArrayContext context) {
        return context.clone();
    }

    private ArrayContext features(ArrayContext context, String f1, double v1) {
        context = context.clone();
        context.put(f1, v1);
        return context;
    }

    private ArrayContext features(ArrayContext context, String f1, Tensor v1) {
        context = context.clone();
        context.put(f1, new TensorValue(v1));
        return context;
    }

    private ArrayContext features(ArrayContext context, String f1, double v1, String f2, double v2) {
        context = context.clone();
        context.put(f1, v1);
        context.put(f2, v2);
        return context;
    }

    private void assertXGBoostEvaluation(double expected, RankingExpression expr, Context context) {
        assertEquals(expected, expr.evaluate(context).asDouble(), 1e-9);
    }

}
