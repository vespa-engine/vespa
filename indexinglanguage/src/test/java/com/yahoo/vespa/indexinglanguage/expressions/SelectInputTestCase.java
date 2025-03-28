// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.collections.Pair;
import com.yahoo.document.DataType;
import com.yahoo.document.Field;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.vespa.indexinglanguage.SimpleTestAdapter;
import org.junit.Test;

import java.util.LinkedList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;

/**
 * @author Simon Thoresen Hult
 */
public class SelectInputTestCase {

    @Test
    public void requireThatAccessorsWork() {
        List<Pair<String, Expression>> cases = new LinkedList<>();
        Pair<String, Expression> foo = new Pair<String, Expression>("foo", new AttributeExpression("foo"));
        Pair<String, Expression> bar = new Pair<String, Expression>("bar", new AttributeExpression("bar"));
        cases.add(foo);
        cases.add(bar);
        SelectInputExpression exp = new SelectInputExpression(cases);

        assertEquals(2, exp.getCases().size());
        assertSame(foo, exp.getCases().get(0));
        assertSame(bar, exp.getCases().get(1));
    }

    @Test
    public void requireThatHashCodeAndEqualsAreImplemented() {
        Expression foo = new AttributeExpression("foo");
        Expression exp = newSelectInput(foo, "bar", "baz");
        assertFalse(exp.equals(new Object()));
        assertFalse(exp.equals(newSelectInput(new IndexExpression("foo"))));
        assertFalse(exp.equals(newSelectInput(new IndexExpression("foo"), "bar")));
        assertFalse(exp.equals(newSelectInput(new IndexExpression("foo"), "bar", "baz")));
        assertFalse(exp.equals(newSelectInput(foo)));
        assertFalse(exp.equals(newSelectInput(foo, "bar")));
        assertEquals(exp, newSelectInput(foo, "bar", "baz"));
        assertEquals(exp.hashCode(), newSelectInput(foo, "bar", "baz").hashCode());
    }

    @Test
    public void requireThatSelectedExpressionIsRun() {
        assertSelect(List.of("foo", "bar"), List.of("foo"), "foo");
        assertSelect(List.of("foo", "bar"), List.of("bar"), "bar");
        assertSelect(List.of("foo", "bar"), List.of("foo", "bar"), "foo");
        assertSelect(List.of("foo", "bar"), List.of("bar", "baz"), "bar");
        assertSelect(List.of("foo", "bar"), List.of("baz", "cox"), null);
    }

    private static SelectInputExpression newSelectInput(Expression exp, String... fieldNames) {
        List<Pair<String, Expression>> cases = new LinkedList<>();
        for (String fieldName : fieldNames) {
            cases.add(new Pair<>(fieldName, exp));
        }
        return new SelectInputExpression(cases);
    }

    private static void assertSelect(List<String> inputField, List<String> availableFields, String expected) {
        SimpleTestAdapter adapter = new SimpleTestAdapter();
        ExecutionContext ctx = new ExecutionContext(adapter);
        for (String fieldName : availableFields) {
            adapter.createField(new Field(fieldName, DataType.STRING));
            ctx.setFieldValue(fieldName, new StringFieldValue(fieldName), null);
        }
        List<Pair<String, Expression>> cases = new LinkedList<>();
        for (String fieldName : inputField) {
            cases.add(new Pair<String, Expression>(fieldName, new SetVarExpression("out")));
        }
        new SelectInputExpression(cases).execute(ctx);
        assertEquals(expected != null ? new StringFieldValue(expected) : null, ctx.getVariable("out"));
    }
}
