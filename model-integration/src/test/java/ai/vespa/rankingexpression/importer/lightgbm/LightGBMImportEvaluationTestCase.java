// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.rankingexpression.importer.lightgbm;

import com.yahoo.searchlib.rankingexpression.RankingExpression;
import com.yahoo.searchlib.rankingexpression.evaluation.ArrayContext;
import com.yahoo.searchlib.rankingexpression.evaluation.ContextIndex;
import com.yahoo.searchlib.rankingexpression.evaluation.DoubleValue;
import com.yahoo.searchlib.rankingexpression.evaluation.ExpressionOptimizer;
import com.yahoo.searchlib.rankingexpression.evaluation.gbdtoptimization.GBDTForestNode;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

/**
 * @author lesters
 */
public class LightGBMImportEvaluationTestCase extends LightGBMTestBase {

    @Test
    public void testRegression() {
        RankingExpression expression = importModel("src/test/models/lightgbm/regression.json");
        ArrayContext context = new ArrayContext(expression, true, DoubleValue.NaN);

        assertEvaluation(1.91300868, expression, features(context));
        assertEvaluation(2.05469776, expression, features(context).add("numerical_1", 0.1).add("numerical_2", 0.2).add("categorical_1", "a").add("categorical_2", "i"));
        assertEvaluation(2.0745534,  expression, features(context).add("numerical_2", 0.5).add("categorical_1", "b").add("categorical_2", "j"));
        assertEvaluation(2.3571838,  expression, features(context).add("numerical_1", 0.7).add("numerical_2", 0.8).add("categorical_2", "m"));

        ExpressionOptimizer optimizer = new ExpressionOptimizer();
        optimizer.optimize(expression, (ContextIndex)context);
        assertTrue(expression.getRoot() instanceof GBDTForestNode);

        assertEvaluation(1.91300868, expression, features(context));
        assertEvaluation(2.05469776, expression, features(context).add("numerical_1", 0.1).add("numerical_2", 0.2).add("categorical_1", "a").add("categorical_2", "i"));
        assertEvaluation(2.0745534,  expression, features(context).add("numerical_2", 0.5).add("categorical_1", "b").add("categorical_2", "j"));
        assertEvaluation(2.3571838,  expression, features(context).add("numerical_1", 0.7).add("numerical_2", 0.8).add("categorical_2", "m"));
    }

    @Test
    public void testClassification() {
        RankingExpression expression = importModel("src/test/models/lightgbm/classification.json");
        ArrayContext context = new ArrayContext(expression, DoubleValue.NaN);
        assertEvaluation(0.37464997, expression, features(context));
        assertEvaluation(0.37464997, expression, features(context).add("numerical_1", 0.1).add("numerical_2", 0.2).add("categorical_1", "a").add("categorical_2", "i"));
        assertEvaluation(0.38730827, expression, features(context).add("numerical_2", 0.5).add("categorical_1", "b").add("categorical_2", "j"));
        assertEvaluation(0.5647872,  expression, features(context).add("numerical_1", 0.7).add("numerical_2", 0.8).add("categorical_2", "m"));
    }

}
