// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
            "CONCEPTTYPE = 0.0\n" +
            "REGEXTYPE = 0.0\n" +
            "POS_18 = 0.0\n" +
            "POS_19 = 0.0\n" +
            "ORDER_IN_CLUSTER = 2.0\n" +
            "GOOD_SYNTAX = 1.0\n" +
            "POS_20 = 0.0\n" +
            "POS_11 = 0.0\n" +
            "POS_10 = 0.0\n" +
            "CHUNKTYPE = 0.0\n" +
            "POS_13 = 0.0\n" +
            "STOP_WORD_1 = 0.0\n" +
            "TERM_CASE_2 = 0.0\n" +
            "TERM_CASE_3 = 0.0\n" +
            "STOP_WORD_3 = 0.0\n" +
            "POS_15 = 0.0\n" +
            "TERM_CASE_1 = 0.0\n" +
            "STOP_WORD_2 = 0.0\n" +
            "POS_1 = 0.0\n" +
            "TERM_CASE_4 = 1.0\n" +
            "LENGTH = 6.0\n" +
            "EXTENDEDTYPE = 0.0\n" +
            "ENTITYPLACETYPE = 0.0\n";

    @Test
    public void testIt() throws ParseException, IOException {
        // Prepare
        RankingExpression expression=new RankingExpression(IOUtils.readFile(new File("src/test/files/s-expression.vre")));
        ArrayContext contextPrototype=new ArrayContext(expression);
        new ExpressionOptimizer().optimize(expression,contextPrototype);

        // Execute
        ArrayContext context=contextPrototype.clone();
        for (String contextValueString : contextString.split("\n")) {
            String[] contextValueParts = contextValueString.split("=");
            context.put(contextValueParts[0].trim(), Double.valueOf(contextValueParts[1].trim()));
        }
        assertEquals(-2.3450294999999994, expression.evaluate(context).asDouble(), 0.000000000001);
    }

}
