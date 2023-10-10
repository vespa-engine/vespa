// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage;

import com.yahoo.collections.Pair;
import com.yahoo.vespa.indexinglanguage.expressions.ArithmeticExpression;
import com.yahoo.vespa.indexinglanguage.expressions.AttributeExpression;
import com.yahoo.vespa.indexinglanguage.expressions.CatExpression;
import com.yahoo.vespa.indexinglanguage.expressions.Expression;
import com.yahoo.vespa.indexinglanguage.expressions.ForEachExpression;
import com.yahoo.vespa.indexinglanguage.expressions.GuardExpression;
import com.yahoo.vespa.indexinglanguage.expressions.IfThenExpression;
import com.yahoo.vespa.indexinglanguage.expressions.IndexExpression;
import com.yahoo.vespa.indexinglanguage.expressions.ParenthesisExpression;
import com.yahoo.vespa.indexinglanguage.expressions.ScriptExpression;
import com.yahoo.vespa.indexinglanguage.expressions.SelectInputExpression;
import com.yahoo.vespa.indexinglanguage.expressions.StatementExpression;
import com.yahoo.vespa.indexinglanguage.expressions.SwitchExpression;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * @author Simon Thoresen Hult
 */
@SuppressWarnings({ "rawtypes" })
public class ExpressionSearcherTestCase {

    private static final ExpressionSearcher searcher = new ExpressionSearcher<>(IndexExpression.class);

    @Test
    public void requireThatExpressionsCanBeFound() {
        IndexExpression exp = new IndexExpression("foo");
        assertFound(exp, new ArithmeticExpression(exp, ArithmeticExpression.Operator.ADD,
                                                  new AttributeExpression("foo")));
        assertFound(exp, new ArithmeticExpression(new AttributeExpression("foo"),
                                                  ArithmeticExpression.Operator.ADD, exp));
        assertFound(exp, new CatExpression(exp));
        assertFound(exp, new CatExpression(new AttributeExpression("foo"), exp));
        assertFound(exp, new ForEachExpression(exp));
        assertFound(exp, new GuardExpression(exp));
        assertFound(exp, new IfThenExpression(exp,
                                              IfThenExpression.Comparator.EQ,
                                              new AttributeExpression("foo"),
                                              new AttributeExpression("bar"),
                                              new AttributeExpression("baz")));
        assertFound(exp, new IfThenExpression(new AttributeExpression("foo"),
                                              IfThenExpression.Comparator.EQ,
                                              exp,
                                              new AttributeExpression("bar"),
                                              new AttributeExpression("baz")));
        assertFound(exp, new IfThenExpression(new AttributeExpression("foo"),
                                              IfThenExpression.Comparator.EQ,
                                              new AttributeExpression("bar"),
                                              exp,
                                              new AttributeExpression("baz")));
        assertFound(exp, new IfThenExpression(new AttributeExpression("foo"),
                                              IfThenExpression.Comparator.EQ,
                                              new AttributeExpression("bar"),
                                              new AttributeExpression("baz"),
                                              exp));
        assertFound(exp, new ParenthesisExpression(exp));
        assertFound(exp, new ScriptExpression(new StatementExpression(exp)));
        assertFound(exp, new ScriptExpression(new StatementExpression(new AttributeExpression("foo")),
                                              new StatementExpression(exp)));
        assertFound(exp, new SelectInputExpression(
                Arrays.asList(new Pair<String, Expression>("foo", exp),
                              new Pair<String, Expression>("bar", new AttributeExpression("bar")))));
        assertFound(exp, new SelectInputExpression(
                Arrays.asList(new Pair<String, Expression>("foo", new AttributeExpression("bar")),
                              new Pair<String, Expression>("bar", exp))));
        assertFound(exp, new StatementExpression(exp));
        assertFound(exp, new StatementExpression(new AttributeExpression("foo"), exp));
        assertFound(exp, new SwitchExpression(
                Collections.singletonMap("foo", exp),
                new AttributeExpression("bar")));
        assertFound(exp, new SwitchExpression(
                Collections.singletonMap("foo", new AttributeExpression("bar")),
                exp));
    }

    private static void assertFound(IndexExpression searchFor, Expression searchIn) {
        assertTrue(searcher.containedIn(searchIn));
        assertSame(searchFor, searcher.searchIn(searchIn));
    }
}
