// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage;

import com.yahoo.collections.Pair;
import com.yahoo.document.datatypes.IntegerFieldValue;
import com.yahoo.language.simple.SimpleLinguistics;
import com.yahoo.vespa.indexinglanguage.expressions.ArithmeticExpression;
import com.yahoo.vespa.indexinglanguage.expressions.AttributeExpression;
import com.yahoo.vespa.indexinglanguage.expressions.Base64DecodeExpression;
import com.yahoo.vespa.indexinglanguage.expressions.Base64EncodeExpression;
import com.yahoo.vespa.indexinglanguage.expressions.CatExpression;
import com.yahoo.vespa.indexinglanguage.expressions.ClearStateExpression;
import com.yahoo.vespa.indexinglanguage.expressions.EchoExpression;
import com.yahoo.vespa.indexinglanguage.expressions.Expression;
import com.yahoo.vespa.indexinglanguage.expressions.ForEachExpression;
import com.yahoo.vespa.indexinglanguage.expressions.GetFieldExpression;
import com.yahoo.vespa.indexinglanguage.expressions.GetVarExpression;
import com.yahoo.vespa.indexinglanguage.expressions.GuardExpression;
import com.yahoo.vespa.indexinglanguage.expressions.HexDecodeExpression;
import com.yahoo.vespa.indexinglanguage.expressions.HexEncodeExpression;
import com.yahoo.vespa.indexinglanguage.expressions.HostNameExpression;
import com.yahoo.vespa.indexinglanguage.expressions.IfThenExpression;
import com.yahoo.vespa.indexinglanguage.expressions.IndexExpression;
import com.yahoo.vespa.indexinglanguage.expressions.InputExpression;
import com.yahoo.vespa.indexinglanguage.expressions.JoinExpression;
import com.yahoo.vespa.indexinglanguage.expressions.LowerCaseExpression;
import com.yahoo.vespa.indexinglanguage.expressions.NormalizeExpression;
import com.yahoo.vespa.indexinglanguage.expressions.NowExpression;
import com.yahoo.vespa.indexinglanguage.expressions.OptimizePredicateExpression;
import com.yahoo.vespa.indexinglanguage.expressions.ParenthesisExpression;
import com.yahoo.vespa.indexinglanguage.expressions.RandomExpression;
import com.yahoo.vespa.indexinglanguage.expressions.ScriptExpression;
import com.yahoo.vespa.indexinglanguage.expressions.SelectInputExpression;
import com.yahoo.vespa.indexinglanguage.expressions.SetLanguageExpression;
import com.yahoo.vespa.indexinglanguage.expressions.SetValueExpression;
import com.yahoo.vespa.indexinglanguage.expressions.SetVarExpression;
import com.yahoo.vespa.indexinglanguage.expressions.SplitExpression;
import com.yahoo.vespa.indexinglanguage.expressions.StatementExpression;
import com.yahoo.vespa.indexinglanguage.expressions.SubstringExpression;
import com.yahoo.vespa.indexinglanguage.expressions.SummaryExpression;
import com.yahoo.vespa.indexinglanguage.expressions.SwitchExpression;
import com.yahoo.vespa.indexinglanguage.expressions.ThisExpression;
import com.yahoo.vespa.indexinglanguage.expressions.ToArrayExpression;
import com.yahoo.vespa.indexinglanguage.expressions.ToByteExpression;
import com.yahoo.vespa.indexinglanguage.expressions.ToDoubleExpression;
import com.yahoo.vespa.indexinglanguage.expressions.ToFloatExpression;
import com.yahoo.vespa.indexinglanguage.expressions.ToIntegerExpression;
import com.yahoo.vespa.indexinglanguage.expressions.ToLongExpression;
import com.yahoo.vespa.indexinglanguage.expressions.ToPositionExpression;
import com.yahoo.vespa.indexinglanguage.expressions.ToStringExpression;
import com.yahoo.vespa.indexinglanguage.expressions.ToWsetExpression;
import com.yahoo.vespa.indexinglanguage.expressions.TokenizeExpression;
import com.yahoo.vespa.indexinglanguage.expressions.TrimExpression;
import com.yahoo.vespa.indexinglanguage.expressions.ZCurveExpression;
import com.yahoo.vespa.indexinglanguage.linguistics.AnnotatorConfig;
import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author Simon Thoresen Hult
 */
public class ExpressionConverterTestCase {

    @Test
    public void requireThatAllExpressionTypesCanBeTraversed() {
        assertConvertable(new ArithmeticExpression(new InputExpression("foo"), ArithmeticExpression.Operator.ADD,
                                                   new InputExpression("bar")));
        assertConvertable(new AttributeExpression("foo"));
        assertConvertable(new Base64DecodeExpression());
        assertConvertable(new Base64EncodeExpression());
        assertConvertable(new CatExpression(new InputExpression("foo"), new IndexExpression("bar")));
        assertConvertable(new ClearStateExpression());
        assertConvertable(new EchoExpression());
        assertConvertable(new ForEachExpression(new IndexExpression("foo")));
        assertConvertable(new GetFieldExpression("foo"));
        assertConvertable(new GetVarExpression("foo"));
        assertConvertable(new GuardExpression(new IndexExpression("foo")));
        assertConvertable(new HexDecodeExpression());
        assertConvertable(new HexEncodeExpression());
        assertConvertable(new HostNameExpression());
        assertConvertable(new IfThenExpression(new InputExpression("foo"), IfThenExpression.Comparator.EQ,
                                               new InputExpression("bar"),
                                               new IndexExpression("baz"),
                                               new IndexExpression("cox")));
        assertConvertable(new IndexExpression("foo"));
        assertConvertable(new InputExpression("foo"));
        assertConvertable(new JoinExpression("foo"));
        assertConvertable(new LowerCaseExpression());
        assertConvertable(new NormalizeExpression(new SimpleLinguistics()));
        assertConvertable(new NowExpression());
        assertConvertable(new OptimizePredicateExpression());
        assertConvertable(new ParenthesisExpression(new InputExpression("foo")));
        assertConvertable(new RandomExpression(69));
        assertConvertable(new ScriptExpression(new StatementExpression(new InputExpression("foo"))));
        assertConvertable(new SelectInputExpression(new Pair<String, Expression>("foo", new IndexExpression("bar")),
                                                    new Pair<String, Expression>("bar", new IndexExpression("foo"))));
        assertConvertable(new SetLanguageExpression());
        assertConvertable(new SetValueExpression(new IntegerFieldValue(69)));
        assertConvertable(new SetVarExpression("foo"));
        assertConvertable(new SplitExpression("foo"));
        assertConvertable(new StatementExpression(new InputExpression("foo")));
        assertConvertable(new SubstringExpression(6, 9));
        assertConvertable(new SummaryExpression("foo"));
        assertConvertable(new SwitchExpression(Collections.singletonMap("foo", (Expression)new IndexExpression("bar")),
                                               new InputExpression("baz")));
        assertConvertable(new ThisExpression());
        assertConvertable(new ToArrayExpression());
        assertConvertable(new ToByteExpression());
        assertConvertable(new ToDoubleExpression());
        assertConvertable(new ToFloatExpression());
        assertConvertable(new ToIntegerExpression());
        assertConvertable(new TokenizeExpression(new SimpleLinguistics(), new AnnotatorConfig()));
        assertConvertable(new ToLongExpression());
        assertConvertable(new ToPositionExpression());
        assertConvertable(new ToStringExpression());
        assertConvertable(new ToWsetExpression(false, false));
        assertConvertable(new TrimExpression());
        assertConvertable(new ZCurveExpression());
    }

    @Test
    public void requireThatScriptElementsCanBeRemoved() {
        StatementExpression foo = new StatementExpression(new AttributeExpression("foo"));
        StatementExpression bar = new StatementExpression(new AttributeExpression("bar"));
        ScriptExpression before = new ScriptExpression(foo, bar);

        Expression after = new SearchReplace(foo, null).convert(before);
        assertTrue(after instanceof ScriptExpression);
        assertEquals(1, ((ScriptExpression)after).size());
        assertEquals(bar, ((ScriptExpression)after).get(0));

        after = new SearchReplace(bar, null).convert(before);
        assertTrue(after instanceof ScriptExpression);
        assertEquals(1, ((ScriptExpression)after).size());
        assertEquals(foo, ((ScriptExpression)after).get(0));
    }

    @Test
    public void requireThatSwitchElementsCanBeRemoved() {
        Map<String, Expression> cases = new HashMap<>();
        Expression foo = new AttributeExpression("foo");
        Expression bar = new AttributeExpression("bar");
        cases.put("foo", foo);
        cases.put("bar", bar);
        SwitchExpression before = new SwitchExpression(cases);

        Expression after = new SearchReplace(foo, null).convert(before);
        assertTrue(after instanceof SwitchExpression);
        assertEquals(1, ((SwitchExpression)after).getCases().size());

        after = new SearchReplace(bar, null).convert(before);
        assertTrue(after instanceof SwitchExpression);
        assertEquals(1, ((SwitchExpression)after).getCases().size());
    }

    @Test
    public void requireThatConversionExceptionCanBeThrown() {
        final RuntimeException expectedCause = new RuntimeException();
        try {
            new ExpressionConverter() {

                @Override
                protected boolean shouldConvert(Expression exp) {
                    return exp instanceof AttributeExpression;
                }

                @Override
                protected Expression doConvert(Expression exp) {
                    throw expectedCause;
                }
            }.convert(new StatementExpression(new AttributeExpression("foo")));
            fail();
        } catch (RuntimeException e) {
            assertSame(expectedCause, e);
        }
    }

    @Test
    public void requireThatCatConversionIgnoresNull() {
        Expression exp = new ExpressionConverter() {

            @Override
            protected boolean shouldConvert(Expression exp) {
                return exp instanceof AttributeExpression;
            }

            @Override
            protected Expression doConvert(Expression exp) {
                return null;
            }
        }.convert(new CatExpression(new AttributeExpression("foo"), new IndexExpression("bar")));
        assertEquals(new CatExpression(new IndexExpression("bar")), exp);
    }

    private static void assertConvertable(Expression exp) {
        MyTraverser traverser = new MyTraverser();
        assertEquals(traverser.convert(exp), exp);
    }

    private static class SearchReplace extends ExpressionConverter {

        final Expression searchFor;
        final Expression replaceWith;

        private SearchReplace(Expression searchFor, Expression replaceWith) {
            this.searchFor = searchFor;
            this.replaceWith = replaceWith;
        }

        @Override
        protected boolean shouldConvert(Expression exp) {
            return exp == searchFor;
        }

        @Override
        protected Expression doConvert(Expression exp) {
            return replaceWith;
        }
    }

    private static class MyTraverser extends ExpressionConverter {

        @Override
        protected boolean shouldConvert(Expression exp) {
            return false;
        }

        @Override
        protected Expression doConvert(Expression exp) {
            return exp;
        }
    }

}
