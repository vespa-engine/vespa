// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.rankingexpression.importer.lightgbm;

import com.yahoo.searchlib.rankingexpression.RankingExpression;
import com.yahoo.searchlib.rankingexpression.evaluation.ArrayContext;
import com.yahoo.searchlib.rankingexpression.evaluation.StringValue;

import static org.junit.Assert.assertEquals;

/**
 * @author lesters
 */
class LightGBMTestBase {

    RankingExpression importModel(String path) {
        return new LightGBMImporter().importModel("lightgbm", path).expressions().get("lightgbm");
    }

    void assertEvaluation(double expected, RankingExpression expr, TestFeatures features) {
        assertEquals(expected, expr.evaluate(features.context).asDouble(), 1e-6);
    }

    TestFeatures features(ArrayContext context) {
        return new TestFeatures(context.clone());
    }

    static class TestFeatures {
        private final ArrayContext context;
        TestFeatures(ArrayContext context) {
            this.context = context;
        }
        TestFeatures add(String name, double value) {
            context.put(name, value);
            return this;
        }
        TestFeatures add(String name, String value) {
            context.put(name, new StringValue(value));
            return this;
        }
    }

}
