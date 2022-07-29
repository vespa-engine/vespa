// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.request;

import com.yahoo.search.grouping.request.parser.ParseException;
import com.yahoo.search.grouping.request.parser.TokenMgrException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Simon Thoresen Hult
 */
public class GroupingOperationTestCase {

    @Test
    void requireThatAccessorsWork() {
        GroupingOperation op = new AllOperation();
        GroupingExpression exp = new AttributeValue("alias");
        op.putAlias("alias", exp);
        assertSame(exp, op.getAlias("alias"));

        assertEquals(0, op.getHints().size());
        assertFalse(op.containsHint("foo"));
        assertFalse(op.containsHint("bar"));

        op.addHint("foo");
        assertEquals(1, op.getHints().size());
        assertTrue(op.containsHint("foo"));
        assertFalse(op.containsHint("bar"));

        op.addHint("bar");
        assertEquals(2, op.getHints().size());
        assertTrue(op.containsHint("foo"));
        assertTrue(op.containsHint("bar"));

        op.setForceSinglePass(true);
        assertTrue(op.getForceSinglePass());
        op.setForceSinglePass(false);
        assertFalse(op.getForceSinglePass());

        exp = new AttributeValue("orderBy");
        op.addOrderBy(exp);
        assertEquals(1, op.getOrderBy().size());
        assertSame(exp, op.getOrderBy(0));

        exp = new AttributeValue("output");
        op.addOutput(exp);
        assertEquals(1, op.getOutputs().size());
        assertSame(exp, op.getOutput(0));

        GroupingOperation child = new AllOperation();
        op.addChild(child);
        assertEquals(1, op.getChildren().size());
        assertSame(child, op.getChild(0));

        exp = new AttributeValue("groupBy");
        op.setGroupBy(exp);
        assertSame(exp, op.getGroupBy());

        op.setWhere("whereA");
        assertEquals("whereA", op.getWhere());
        op.setWhere("whereB");
        assertEquals("whereB", op.getWhere());

        op.setAccuracy(0.6);
        assertEquals(0.6, op.getAccuracy(), 1E-6);
        op.setAccuracy(0.9);
        assertEquals(0.9, op.getAccuracy(), 1E-6);

        op.setPrecision(6);
        assertEquals(6, op.getPrecision());
        op.setPrecision(9);
        assertEquals(9, op.getPrecision());

        assertFalse(op.hasMax());
        op.setMax(6);
        assertTrue(op.hasMax());
        assertEquals(6, op.getMax());
        op.setMax(9);
        assertEquals(9, op.getMax());
        assertTrue(op.hasMax());
        op.setMax(0);
        assertTrue(op.hasMax());
        op.setMax(-7);
        assertFalse(op.hasMax());
    }

    @Test
    void requireThatFromStringAsListParsesAllOperations() {
        List<GroupingOperation> lst = GroupingOperation.fromStringAsList("");
        assertTrue(lst.isEmpty());

        lst = GroupingOperation.fromStringAsList("all()");
        assertEquals(1, lst.size());
        assertTrue(lst.get(0) instanceof AllOperation);

        lst = GroupingOperation.fromStringAsList("each()");
        assertEquals(1, lst.size());
        assertTrue(lst.get(0) instanceof EachOperation);

        lst = GroupingOperation.fromStringAsList("all();each()");
        assertEquals(2, lst.size());
        assertTrue(lst.get(0) instanceof AllOperation);
        assertTrue(lst.get(1) instanceof EachOperation);
    }

    @Test
    void requireThatFromStringAcceptsOnlyOneOperation() {
        try {
            GroupingOperation.fromString("");
            fail();
        } catch (IllegalArgumentException e) {

        }
        assertTrue(GroupingOperation.fromString("all()") instanceof AllOperation);
        assertTrue(GroupingOperation.fromString("each()") instanceof EachOperation);
        try {
            GroupingOperation.fromString("all();each()");
            fail();
        } catch (IllegalArgumentException e) {

        }
    }

    @Test
    void requireThatParseExceptionsAreRethrown() {
        try {
            GroupingOperation.fromString("all(foo)");
            fail();
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().startsWith("Encountered \" <IDENTIFIER> \"foo\"\" at line 1, column 5.\n"));
            assertTrue(e.getCause() instanceof ParseException);
        }
    }

    @Test
    void requireThatTokenErrorsAreRethrown() {
        try {
            GroupingOperation.fromString("all(\\foo)");
            fail();
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().startsWith("Lexical error at line 1, column 6."));
            assertTrue(e.getCause() instanceof TokenMgrException);
        }
    }
}
