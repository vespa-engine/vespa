// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.datatypes.IntegerFieldValue;
import com.yahoo.document.datatypes.StringFieldValue;
import org.junit.Test;

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

        assertNotEquals(exp, new Object());
        assertNotEquals(exp, new SwitchExpression(Map.of("foo", foo)));
        assertNotEquals(exp, new SwitchExpression(Map.of("foo", foo), baz));
        assertNotEquals(exp, new SwitchExpression(cases));
        assertEquals(exp, new SwitchExpression(cases, baz));
        assertEquals(exp.hashCode(), new SwitchExpression(cases, baz).hashCode());
    }

    @Test
    public void requireThatExpressionCanBeVerified() {
        Expression foo = SimpleExpression.newConversion(DataType.STRING, DataType.INT);
        Expression exp = new SwitchExpression(Map.of("foo", foo));
        assertVerify(DataType.STRING, exp, DataType.STRING); // does not touch output
        assertVerifyThrows("Invalid expression 'switch { case \"foo\": SimpleExpression; }': Expected string input, but no input is specified", null, exp);
        assertVerifyThrows("Invalid expression 'switch { case \"foo\": SimpleExpression; }': Expected string input, got int", DataType.INT, exp);
    }

    @Test
    public void requireThatCasesAreVerified() {
        Map<String, Expression> cases = new HashMap<>();
        cases.put("foo", SimpleExpression.newRequired(DataType.INT));
        assertVerifyThrows("Invalid expression 'SimpleExpression': Expected int input, got string", DataType.STRING, new SwitchExpression(cases)
                          );
        assertVerifyThrows("Invalid expression 'SimpleExpression': Expected int input, got string", DataType.STRING, new SwitchExpression(Map.of(), SimpleExpression.newRequired(DataType.INT))
                          );
    }

    @Test
    public void requireThatIllegalArgumentThrows() {
        try {
            new SwitchExpression(Map.of()).execute(new IntegerFieldValue(69));
            fail();
        } catch (IllegalArgumentException e) {
            assertEquals("Expected string input, got int", e.getMessage());
        }
    }

    @Test
    public void requireThatDefaultExpressionIsNullIfNotGiven() {
        assertNull(new SwitchExpression(Map.of()).getDefaultExpression());
    }

    @Test
    public void requireThatIsEmptyReflectsOnBothCasesAndDefault() {
        assertTrue(new SwitchExpression(Map.of()).isEmpty());
        assertTrue(new SwitchExpression(Map.of(), null).isEmpty());
        assertFalse(new SwitchExpression(Map.of(),
                                         new AttributeExpression("foo")).isEmpty());
        assertFalse(new SwitchExpression(Map.of("foo", new AttributeExpression("foo")),
                                         null).isEmpty());
        assertFalse(new SwitchExpression(Map.of("foo", new AttributeExpression("foo")),
                                         new AttributeExpression("foo")).isEmpty());

    }

    @Test
    public void requireThatCorrectExpressionIsExecuted() {
        Map<String, Expression> cases = new HashMap<>();
        cases.put("foo", new StatementExpression(new ConstantExpression(new StringFieldValue("bar")),
                                                 new SetVarExpression("out")));
        cases.put("baz", new StatementExpression(new ConstantExpression(new StringFieldValue("cox")),
                                                 new SetVarExpression("out")));
        Expression exp = new SwitchExpression(cases);
        assertEvaluate(new StringFieldValue("foo"), exp, new StringFieldValue("bar"));
        assertEvaluate(new StringFieldValue("baz"), exp, new StringFieldValue("cox"));
        assertEvaluate(new StringFieldValue("???"), exp, null);
    }

    @Test
    public void requireThatDefaultExpressionIsExecuted() {
        Map<String, Expression> cases = new HashMap<>();
        cases.put("foo", new StatementExpression(new ConstantExpression(new StringFieldValue("bar")),
                                                 new SetVarExpression("out")));
        Expression defaultExp = new StatementExpression(new ConstantExpression(new StringFieldValue("cox")),
                                                        new SetVarExpression("out"));
        Expression exp = new SwitchExpression(cases, defaultExp);
        assertEvaluate(new StringFieldValue("foo"), exp, new StringFieldValue("bar"));
        assertEvaluate(new StringFieldValue("baz"), exp, new StringFieldValue("cox"));
        assertEvaluate(null, exp, new StringFieldValue("cox"));
    }

    private static void assertEvaluate(FieldValue input, Expression exp, FieldValue expectedOutVar) {
        assertEquals(expectedOutVar, new ExecutionContext().setCurrentValue(input).execute(exp).getVariable("out"));
    }
}
