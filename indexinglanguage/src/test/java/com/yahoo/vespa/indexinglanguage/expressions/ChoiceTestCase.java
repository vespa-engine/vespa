// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.Field;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.vespa.indexinglanguage.SimpleTestAdapter;
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
            ExecutionContext context = new ExecutionContext(adapter);
            choice.execute(context);
            assertEquals("foo1", context.getValue().getWrappedValue());
        }
    }

}
