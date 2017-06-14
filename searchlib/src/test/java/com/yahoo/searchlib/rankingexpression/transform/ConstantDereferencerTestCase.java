// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression.transform;

import com.yahoo.searchlib.rankingexpression.RankingExpression;
import com.yahoo.searchlib.rankingexpression.evaluation.Value;
import com.yahoo.searchlib.rankingexpression.parser.ParseException;
import org.junit.Test;
import static org.junit.Assert.*;

import java.util.HashMap;
import java.util.Map;

/**
 * @author bratseth
 */
public class ConstantDereferencerTestCase {

    @Test
    public void testConstantDereferencer() throws ParseException {
        Map<String, Value> constants = new HashMap<>();
        constants.put("a", Value.parse("1.0"));
        constants.put("b", Value.parse("2"));
        constants.put("c", Value.parse("3.5"));
        ConstantDereferencer c = new ConstantDereferencer(constants);

        assertEquals("1.0 + 2.0 + 3.5", c.transform(new RankingExpression("a + b + c")).toString());
        assertEquals("myMacro(1.0,2.0)", c.transform(new RankingExpression("myMacro(a, b)")).toString());
    }

}
