// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression.evaluation.tensoroptimization;

import com.yahoo.searchlib.rankingexpression.RankingExpression;
import com.yahoo.searchlib.rankingexpression.evaluation.ArrayContext;
import com.yahoo.searchlib.rankingexpression.evaluation.ExpressionOptimizer;
import com.yahoo.searchlib.rankingexpression.evaluation.OptimizationReport;
import com.yahoo.searchlib.rankingexpression.evaluation.TensorValue;
import com.yahoo.searchlib.rankingexpression.parser.ParseException;
import com.yahoo.searchlib.rankingexpression.rule.TensorFunctionNode;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.functions.Reduce;
import com.yahoo.tensor.functions.ReduceJoin;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author lesters
 */
public class TensorOptimizerTestCase {

    @Test
    public void testReduceJoinOptimization() throws ParseException {
        assertWillOptimize("d0[3]", "d0[3]");
        assertWillOptimize("d0[1]", "d0[1]", "d0");
        assertWillOptimize("d0[2]", "d0[2]", "d0");
        assertWillOptimize("d0[3]", "d0[3]", "d0");
        assertWillOptimize("d0[3]", "d0[3],d1[2]", "d0");
        assertWillOptimize("d0[3],d1[2]", "d0[3]", "d0");
        assertWillOptimize("d1[3]", "d0[2],d1[3]", "d1");
        assertWillOptimize("d0[2],d1[3]", "d1[3]", "d1");
        assertWillOptimize("d0[2],d2[2]", "d1[3],d2[2]", "d2");
        assertWillOptimize("d1[2],d2[2]", "d0[3],d2[2]", "d2");
        assertWillOptimize("d0[1],d2[4]", "d1[3],d2[4]", "d2");
        assertWillOptimize("d0[2],d2[4]", "d1[3],d2[4]", "d2");
        assertWillOptimize("d0[2],d1[3]", "d0[2],d1[3]");
        assertWillOptimize("d0[2],d1[3]", "d0[2],d1[3]", "d0,d1");
        assertWillOptimize("d2[3],d3[4]", "d1[2],d2[3],d3[4]", "d2,d3");
        assertWillOptimize("d0[1],d2[3],d3[4]", "d1[2],d2[3],d3[4]", "d2,d3");
        assertWillOptimize("d0[1],d1[2],d2[3]", "d2[3],d3[4],d4[5]", "d2");
        assertWillOptimize("d0[1],d1[2],d2[3]", "d1[2],d2[3],d4[4]", "d1,d2");
        assertWillOptimize("d0[1],d1[2],d2[3]", "d0[1],d1[2],d2[3]");
        assertWillOptimize("d0[1],d1[2],d2[3]", "d0[1],d1[2],d2[3]", "d0,d1,d2");

        // Will not currently use reduce-join optimization
        assertCantOptimize("d0[2],d1[3]", "d1[3]", "d0");  // reducing on a dimension not joining on
        assertCantOptimize("d0[1],d1[2]", "d1[2],d2[3]", "d2");  // same
        assertCantOptimize("d0[3]", "d0[3],d1[2]");  // reducing on more then we are combining
        assertCantOptimize("d0[1],d2[3]", "d1[2],d2[3]");  // same
        assertCantOptimize("d0[1],d1[2],d2[3]", "d0[1],d1[2],d2[3]", "d1,d2");  // reducing on less then joining on
    }

    private void assertWillOptimize(String aType, String bType) throws ParseException {
        assertWillOptimize(aType, bType, "", "sum");
    }

    private void assertWillOptimize(String aType, String bType, String reduceDim) throws ParseException {
        assertWillOptimize(aType, bType, reduceDim, "sum");
    }

    private void assertWillOptimize(String aType, String bType, String reduceDim, String aggregator) throws ParseException {
        assertReduceJoin(aType, bType, reduceDim, aggregator, true);
    }

    private void assertCantOptimize(String aType, String bType) throws ParseException {
        assertCantOptimize(aType, bType, "", "sum");
    }

    private void assertCantOptimize(String aType, String bType, String reduceDim) throws ParseException {
        assertCantOptimize(aType, bType, reduceDim, "sum");
    }

    private void assertCantOptimize(String aType, String bType, String reduceDim, String aggregator) throws ParseException {
        assertReduceJoin(aType, bType, reduceDim, aggregator, false);
    }

    private void assertReduceJoin(String aType, String bType, String reduceDim, String aggregator, boolean assertOptimize) throws ParseException {
        Tensor a = generateRandomTensor(aType);
        Tensor b = generateRandomTensor(bType);
        RankingExpression expression = generateRankingExpression(reduceDim, aggregator);
        assert ((TensorFunctionNode)expression.getRoot()).function() instanceof Reduce;

        ArrayContext context = generateContext(a, b, expression);
        Tensor result = expression.evaluate(context).asTensor();

        ExpressionOptimizer optimizer = new ExpressionOptimizer();
        OptimizationReport report = optimizer.optimize(expression, context);
        assertEquals(1, report.getMetric("Replaced reduce->join"));
        assert ((TensorFunctionNode)expression.getRoot()).function() instanceof ReduceJoin;

        assertEquals(result, expression.evaluate(context).asTensor());
        assertEquals(assertOptimize, ((ReduceJoin)((TensorFunctionNode)expression.getRoot()).function()).canOptimize(a, b));
    }

    private RankingExpression generateRankingExpression(String reduceDim, String aggregator) throws ParseException {
        String dimensions = "";
        if (reduceDim.length() > 0) {
            dimensions = ", " + reduceDim;
        }
        return new RankingExpression("reduce(join(a, b, f(a,b)(a * b)), " + aggregator + dimensions + ")");
    }

    private ArrayContext generateContext(Tensor a, Tensor b, RankingExpression expression) {
        ArrayContext context = new ArrayContext(expression);
        context.put("a", new TensorValue(a));
        context.put("b", new TensorValue(b));
        return context;
    }

    private Tensor generateRandomTensor(String type) {
        return Tensor.random(TensorType.fromSpec("tensor(" + type + ")"));
    }

}
