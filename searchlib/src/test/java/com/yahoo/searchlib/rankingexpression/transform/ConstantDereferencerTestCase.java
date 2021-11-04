// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression.transform;

import com.yahoo.searchlib.rankingexpression.RankingExpression;
import com.yahoo.searchlib.rankingexpression.evaluation.MapTypeContext;
import com.yahoo.searchlib.rankingexpression.evaluation.Value;
import com.yahoo.searchlib.rankingexpression.parser.ParseException;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

/**
 * @author bratseth
 */
public class ConstantDereferencerTestCase {

    @Test
    public void testConstantDereferencer() throws ParseException {
        ConstantDereferencer c = new ConstantDereferencer();

        Map<String, Value> constants = new HashMap<>();
        constants.put("a", Value.parse("1.0"));
        constants.put("b", Value.parse("2"));
        constants.put("c", Value.parse("3.5"));
        TransformContext context = new TransformContext(constants, new MapTypeContext());

        assertEquals("1.0 + 2 + 3.5", c.transform(new RankingExpression("a + b + c"), context).toString());
        assertEquals("myFunction(1.0,2)", c.transform(new RankingExpression("myFunction(a, b)"), context).toString());
        assertEquals("tensor(x[2],y[3])((x + y == 1.0))", c.transform(new RankingExpression("tensor(x[2],y[3])(x+y==a)"), context).toString());

    }

}
