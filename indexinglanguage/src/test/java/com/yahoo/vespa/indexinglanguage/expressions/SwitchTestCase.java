// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.datatypes.IntegerFieldValue;
import com.yahoo.document.datatypes.StringFieldValue;
import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static com.yahoo.vespa.indexinglanguage.expressions.ExpressionAssert.assertVerify;
import static com.yahoo.vespa.indexinglanguage.expressions.ExpressionAssert.assertVerifyThrows;
import static org.junit.Assert.*;

/**
 * @author Simon Thoresen Hult
 */
public class SwitchTestCase {

    @Test
    public void requireThatAccessorsWork() {
        Map<String, Expression> cases = new HashMap<>();
        Expression foo = new AttributeExpression("foo");
        Expression bar = new AttributeExpression("bar");
        Expression baz = new AttributeExpression("baz");
        cases.put("foo", foo);
        cases.put("bar", bar);
        SwitchExpression exp = new SwitchExpression(cases, baz);
        assertEquals(2, exp.getCases().size());
        assertSame(foo, exp.getCases().get("foo"));
        assertSame(bar, exp.getCases().get("bar"));
        assertSame(baz, exp.getDefaultExpression());
    }

    @Test
    public void requireThatHashCodeAndEqualsAreImplemented() {
        Map<String, Expression> cases = new HashMap<>();
        Expression foo = new AttributeExpression("foo");
        Expression bar = new AttributeExpression("bar");
        Expression baz = new AttributeExpression("baz");
        cases.put("foo", foo);
        cases.put("bar", bar);
        SwitchExpression exp = new SwitchExpression(cases, baz);

        assertFalse(exp.equals(new Object()));
        assertFalse(exp.equals(new SwitchExpression(Collections.singletonMap("foo", foo))));
        assertFalse(exp.equals(new SwitchExpression(Collections.singletonMap("foo", foo), baz)));
        assertFalse(exp.equals(new SwitchExpression(cases)));
        assertEquals(exp, new SwitchExpression(cases, baz));
        assertEquals(exp.hashCode(), new SwitchExpression(cases, baz).hashCode());
    }

    @Test
    public void requireThatExpressionCanBeVerified() {
        Expression foo = SimpleExpression.newConversion(DataType.STRING, DataType.INT);
        Expression exp = new SwitchExpression(Collections.singletonMap("foo", foo));
        assertVerify(DataType.STRING, exp, DataType.STRING); // does not touch output
        assertVerifyThrows(null, exp, "Expected string input, got null.");
        assertVerifyThrows(DataType.INT, exp, "Expected string input, got int.");
    }

    @Test
    public void requireThatCasesAreVerified() {
        Map<String, Expression> cases = new HashMap<>();
        cases.put("foo", SimpleExpression.newRequired(DataType.INT));
        assertVerifyThrows(DataType.STRING, new SwitchExpression(cases),
                           "Expected int input, got string.");
        assertVerifyThrows(DataType.STRING, new SwitchExpression(Collections.<String, Expression>emptyMap(),
                                                                 SimpleExpression.newRequired(DataType.INT)),
                           "Expected int input, got string.");
    }

    @Test
    public void requireThatIllegalArgumentThrows() {
        try {
            new SwitchExpression(Collections.<String, Expression>emptyMap()).execute(new IntegerFieldValue(69));
            fail();
        } catch (IllegalArgumentException e) {
            assertEquals("Expected string input, got int.", e.getMessage());
        }
    }

    @Test
    public void requireThatDefaultExpressionIsNullIfNotGiven() {
        assertNull(new SwitchExpression(Collections.<String, Expression>emptyMap()).getDefaultExpression());
    }

    @Test
    public void requireThatIsEmptyReflectsOnBothCasesAndDefault() {
        assertTrue(new SwitchExpression(Collections.<String, Expression>emptyMap()).isEmpty());
        assertTrue(new SwitchExpression(Collections.<String, Expression>emptyMap(), null).isEmpty());
        assertFalse(new SwitchExpression(Collections.<String, Expression>emptyMap(),
                                         new AttributeExpression("foo")).isEmpty());
        assertFalse(new SwitchExpression(Collections.singletonMap("foo", new AttributeExpression("foo")),
                                         null).isEmpty());
        assertFalse(new SwitchExpression(Collections.singletonMap("foo", new AttributeExpression("foo")),
                                         new AttributeExpression("foo")).isEmpty());

    }

    @Test
    public void requireThatCorrectExpressionIsExecuted() {
        Map<String, Expression> cases = new HashMap<>();
        cases.put("foo", new StatementExpression(new SetValueExpression(new StringFieldValue("bar")),
                                                 new SetVarExpression("out")));
        cases.put("baz", new StatementExpression(new SetValueExpression(new StringFieldValue("cox")),
                                                 new SetVarExpression("out")));
        Expression exp = new SwitchExpression(cases);
        assertEvaluate(new StringFieldValue("foo"), exp, new StringFieldValue("bar"));
        assertEvaluate(new StringFieldValue("baz"), exp, new StringFieldValue("cox"));
        assertEvaluate(new StringFieldValue("???"), exp, null);
    }

    @Test
    public void requireThatDefaultExpressionIsExecuted() {
        Map<String, Expression> cases = new HashMap<>();
        cases.put("foo", new StatementExpression(new SetValueExpression(new StringFieldValue("bar")),
                                                 new SetVarExpression("out")));
        Expression defaultExp = new StatementExpression(new SetValueExpression(new StringFieldValue("cox")),
                                                        new SetVarExpression("out"));
        Expression exp = new SwitchExpression(cases, defaultExp);
        assertEvaluate(new StringFieldValue("foo"), exp, new StringFieldValue("bar"));
        assertEvaluate(new StringFieldValue("baz"), exp, new StringFieldValue("cox"));
        assertEvaluate(null, exp, new StringFieldValue("cox"));
    }

    private static void assertEvaluate(FieldValue input, Expression exp, FieldValue expectedOutVar) {
        assertEquals(expectedOutVar, new ExecutionContext().setValue(input).execute(exp).getVariable("out"));
    }
}
