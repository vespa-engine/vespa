// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.annotation.*;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.vespa.indexinglanguage.SimpleTestAdapter;
import org.junit.Test;

import java.util.Iterator;

import static com.yahoo.vespa.indexinglanguage.expressions.ExpressionAssert.assertVerify;
import static com.yahoo.vespa.indexinglanguage.expressions.ExpressionAssert.assertVerifyThrows;
import static org.junit.Assert.*;

/**
 * @author Simon Thoresen Hult
 */
public class ExactTestCase {

    @Test
    public void requireThatHashCodeAndEqualsAreImplemented() {
        Expression exp = new ExactExpression();
        assertNotEquals(exp, new Object());
        assertEquals(exp, new ExactExpression());
        assertEquals(exp.hashCode(), new ExactExpression().hashCode());
    }

    @Test
    public void requireThatValueIsNotChanged() {
        ExecutionContext ctx = new ExecutionContext(new SimpleTestAdapter());
        ctx.setValue(new StringFieldValue("FOO"));
        new ExactExpression().execute(ctx);

        assertEquals("FOO", String.valueOf(ctx.getValue()));
    }

    @Test
    public void requireThatValueIsAnnotated() {
        ExecutionContext ctx = new ExecutionContext(new SimpleTestAdapter());
        ctx.setValue(new StringFieldValue("FOO"));
        new ExactExpression().execute(ctx);

        assertAnnotation(0, 3, new StringFieldValue("foo"), (StringFieldValue)ctx.getValue());
    }

    @Test
    public void requireThatThereIsNoSegmentation() {
        ExecutionContext ctx = new ExecutionContext(new SimpleTestAdapter());
        ctx.setValue(new StringFieldValue("FOO BAR"));
        new ExactExpression().execute(ctx);

        assertAnnotation(0, 7, new StringFieldValue("foo bar"), (StringFieldValue)ctx.getValue());
    }

    @Test
    public void requireThatRedundantAnnotationValueIsIgnored() {
        ExecutionContext ctx = new ExecutionContext(new SimpleTestAdapter());
        ctx.setValue(new StringFieldValue("foo"));
        new ExactExpression().execute(ctx);

        assertAnnotation(0, 3, null, (StringFieldValue)ctx.getValue());
    }

    @Test
    public void requireThatEmptyStringsAreNotAnnotated() {
        ExecutionContext ctx = new ExecutionContext(new SimpleTestAdapter());
        ctx.setValue(new StringFieldValue(""));
        new ExactExpression().execute(ctx);

        assertNull(((StringFieldValue)ctx.getValue()).getSpanTree(SpanTrees.LINGUISTICS));
    }

    @Test
    public void requireThatExpressionCanBeVerified() {
        Expression exp = new ExactExpression();
        assertVerify(DataType.STRING, exp, DataType.STRING);
        assertVerifyThrows(null, exp, "Expected string input, got null.");
        assertVerifyThrows(DataType.INT, exp, "Expected string input, got int.");
    }

    private static void assertAnnotation(int expectedFrom, int expectedLen, StringFieldValue expectedVal,
                                         StringFieldValue actualVal)
    {
        SpanTree tree = actualVal.getSpanTree(SpanTrees.LINGUISTICS);
        assertNotNull(tree);
        SpanList root = (SpanList)tree.getRoot();
        assertNotNull(root);

        Iterator<SpanNode> nodeIt = root.childIterator();
        assertTrue(nodeIt.hasNext());
        SpanNode node = nodeIt.next();
        assertNotNull(node);
        assertEquals(expectedFrom, node.getFrom());
        assertEquals(expectedLen, node.getLength());

        Iterator<Annotation> annoIt = tree.iterator(node);
        assertTrue(annoIt.hasNext());
        Annotation anno = annoIt.next();
        assertNotNull(anno);
        assertEquals(expectedVal, anno.getFieldValue());
    }
}
