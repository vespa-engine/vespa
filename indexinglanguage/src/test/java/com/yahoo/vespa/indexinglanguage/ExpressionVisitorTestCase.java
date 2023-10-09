// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
import com.yahoo.vespa.indexinglanguage.expressions.ConstantExpression;
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

import static org.junit.Assert.assertEquals;

/**
 * @author Simon Thoresen Hult
 */
public class ExpressionVisitorTestCase {

    @SuppressWarnings("unchecked")
    @Test
    public void requireThatAllExpressionsAreVisited() {
        assertCount(3, new ArithmeticExpression(new InputExpression("foo"), ArithmeticExpression.Operator.ADD,
                                                new InputExpression("bar")));
        assertCount(1, new AttributeExpression("foo"));
        assertCount(1, new Base64DecodeExpression());
        assertCount(1, new Base64EncodeExpression());
        assertCount(3, new CatExpression(new InputExpression("foo"), new IndexExpression("bar")));
        assertCount(1, new ClearStateExpression());
        assertCount(1, new EchoExpression());
        assertCount(2, new ForEachExpression(new IndexExpression("foo")));
        assertCount(1, new GetFieldExpression("foo"));
        assertCount(1, new GetVarExpression("foo"));
        assertCount(2, new GuardExpression(new IndexExpression("foo")));
        assertCount(1, new HexDecodeExpression());
        assertCount(1, new HexEncodeExpression());
        assertCount(1, new HostNameExpression());
        assertCount(5, new IfThenExpression(new InputExpression("foo"), IfThenExpression.Comparator.EQ,
                                            new InputExpression("bar"),
                                            new IndexExpression("baz"),
                                            new IndexExpression("cox")));
        assertCount(1, new IndexExpression("foo"));
        assertCount(1, new InputExpression("foo"));
        assertCount(1, new JoinExpression("foo"));
        assertCount(1, new LowerCaseExpression());
        assertCount(1, new NormalizeExpression(new SimpleLinguistics()));
        assertCount(1, new NowExpression());
        assertCount(1, new OptimizePredicateExpression());
        assertCount(2, new ParenthesisExpression(new InputExpression("foo")));
        assertCount(1, new RandomExpression(69));
        assertCount(3, new ScriptExpression(new StatementExpression(new InputExpression("foo"))));
        assertCount(3, new SelectInputExpression(new Pair<String, Expression>("foo", new IndexExpression("bar")),
                                                 new Pair<String, Expression>("bar", new IndexExpression("foo"))));
        assertCount(1, new SetLanguageExpression());
        assertCount(1, new ConstantExpression(new IntegerFieldValue(69)));
        assertCount(1, new SetVarExpression("foo"));
        assertCount(1, new SplitExpression("foo"));
        assertCount(2, new StatementExpression(new InputExpression("foo")));
        assertCount(1, new SummaryExpression("foo"));
        assertCount(1, new SubstringExpression(6, 9));
        assertCount(3, new SwitchExpression(Collections.singletonMap("foo", (Expression)new IndexExpression("bar")),
                                            new InputExpression("baz")));
        assertCount(1, new ThisExpression());
        assertCount(1, new ToArrayExpression());
        assertCount(1, new ToByteExpression());
        assertCount(1, new ToDoubleExpression());
        assertCount(1, new ToFloatExpression());
        assertCount(1, new ToIntegerExpression());
        assertCount(1, new TokenizeExpression(new SimpleLinguistics(), new AnnotatorConfig()));
        assertCount(1, new ToLongExpression());
        assertCount(1, new ToPositionExpression());
        assertCount(1, new ToStringExpression());
        assertCount(1, new ToWsetExpression(false, false));
        assertCount(1, new TrimExpression());
        assertCount(1, new ZCurveExpression());
    }

    private static void assertCount(int expectedCount, Expression exp) {
        MyVisitor visitor = new MyVisitor();
        visitor.visit(exp);
        assertEquals(expectedCount, visitor.count);
    }

    private static class MyVisitor extends ExpressionVisitor {

        int count = 0;

        @Override
        protected void doVisit(Expression exp) {
            ++count;
        }
    }
}
