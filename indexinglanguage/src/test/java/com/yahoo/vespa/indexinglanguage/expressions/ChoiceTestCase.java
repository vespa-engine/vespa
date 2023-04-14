// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.Document;
import com.yahoo.document.Field;
import com.yahoo.document.datatypes.LongFieldValue;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.language.Linguistics;
import com.yahoo.language.process.Embedder;
import com.yahoo.language.simple.SimpleLinguistics;
import com.yahoo.vespa.indexinglanguage.ExpressionSearcher;
import com.yahoo.vespa.indexinglanguage.SimpleTestAdapter;
import com.yahoo.vespa.indexinglanguage.parser.ParseException;
import com.yahoo.yolean.Exceptions;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author bratseth
 */
public class ChoiceTestCase {

    @Test
    public void testChoiceExecution() {
        var choice = new ChoiceExpression(new InputExpression("foo"), new InputExpression("bar"));

        {   // foo only
            var adapter = new SimpleTestAdapter(new Field("foo", DataType.STRING));
            adapter.setValue("foo", new StringFieldValue("foo1"));
            ExecutionContext context = new ExecutionContext(adapter);
            choice.execute(context);
            assertEquals("foo1", context.getValue().getWrappedValue());
        }

        {   // bar only
            var adapter = new SimpleTestAdapter(new Field("bar", DataType.STRING));
            adapter.setValue("bar", new StringFieldValue("bar1"));
            ExecutionContext context = new ExecutionContext(adapter);
            choice.execute(context);
            assertEquals("bar1", context.getValue().getWrappedValue());
        }

        {   // both foo and bar
            var adapter = new SimpleTestAdapter(new Field("foo", DataType.STRING), new Field("bar", DataType.STRING));
            adapter.setValue("foo", new StringFieldValue("foo1"));
            adapter.setValue("bar", new StringFieldValue("bar1"));
            choice.verify(adapter);
            ExecutionContext context = new ExecutionContext(adapter);
            choice.execute(context);
            assertEquals("foo1", context.getValue().getWrappedValue());
        }
    }

    @Test
    public void testChoiceWithConstant() throws ParseException {
        var choice = parse("input timestamp || 99999999L | attribute timestamp");

        { // value is set
            var adapter = new SimpleTestAdapter(new Field("timestamp", DataType.LONG));
            choice.verify(adapter);
            adapter.setValue("timestamp", new LongFieldValue(34));
            ExecutionContext context = new ExecutionContext(adapter);
            choice.execute(context);
            assertEquals(34L, context.getValue().getWrappedValue());
        }

        { // fallback to default
            var adapter = new SimpleTestAdapter(new Field("timestamp", DataType.LONG));
            choice.verify(adapter);
            ExecutionContext context = new ExecutionContext(adapter);
            choice.execute(context);
            assertEquals(99999999L, context.getValue().getWrappedValue());
        }
    }

    @Test
    public void testIllegalChoiceExpression() throws ParseException {
        try {
            parse("input (foo || 99999999) | attribute");
        }
        catch (IllegalArgumentException e) {
            assertEquals("'input' must be given a field name as argument", Exceptions.toMessageString(e));
        }
    }

    @Test
    public void testInnerConvert() throws ParseException {
        var expression = parse("(input foo || 99999999) | attribute");
        new ExpressionSearcher<>(AttributeExpression.class).searchIn(expression); // trigger innerConvert
    }

    private static Expression parse(String s) throws ParseException {
        return Expression.fromString(s, new SimpleLinguistics(), Embedder.throwsOnUse.asMap());
    }

}
