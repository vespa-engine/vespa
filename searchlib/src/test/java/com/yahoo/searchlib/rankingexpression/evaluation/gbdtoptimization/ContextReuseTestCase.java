// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression.evaluation.gbdtoptimization;

import com.yahoo.io.IOUtils;
import com.yahoo.searchlib.rankingexpression.RankingExpression;
import com.yahoo.searchlib.rankingexpression.evaluation.ArrayContext;
import com.yahoo.searchlib.rankingexpression.evaluation.ExpressionOptimizer;
import com.yahoo.searchlib.rankingexpression.parser.ParseException;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.assertEquals;

/**
 * This tests reuse of a optimized context which is not initialized with
 * all values referenced in the expression.
 *
 * @author bratseth
 */
public class ContextReuseTestCase {

    private String contextString =
            "ORDER_IN_CLUSTER = 2.0\n" +
            "GOOD_SYNTAX = 1.0\n" +
            "TERM_CASE_4 = 1.0\n" +
            "LENGTH = 6.0\n";

    private static final double delta = 0.00000001;

    @Test
    public void testIt() throws ParseException, IOException {
        // Prepare
        RankingExpression expression = new RankingExpression(IOUtils.readFile(new File("src/test/files/s-expression.vre")));
        ArrayContext contextPrototype = new ArrayContext(expression);
        new ExpressionOptimizer().optimize(expression, contextPrototype);

        assertExecution(expression, contextPrototype);
        assertExecution(expression, contextPrototype); // reuse
    }

    private void assertExecution(RankingExpression expression, ArrayContext contextPrototype) {
        ArrayContext context = contextPrototype.clone();
        for (String contextValueString : contextString.split("\n")) {
            String[] contextValueParts = contextValueString.split("=");
            context.put(contextValueParts[0].trim(), Double.valueOf(contextValueParts[1].trim()));
        }
        assertEquals("Context values not set are initialized to 0 doubles",
                     0.0, context.get("CHUNKTYPE").asDouble(), delta);
        assertEquals(-2.3450294999999994, expression.evaluate(context).asDouble(), delta);
    }

}
