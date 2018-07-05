// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.annotation.*;
import com.yahoo.document.datatypes.StringFieldValue;
import org.junit.Test;

import static com.yahoo.vespa.indexinglanguage.expressions.ExpressionAssert.assertVerify;
import static com.yahoo.vespa.indexinglanguage.expressions.ExpressionAssert.assertVerifyThrows;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * @author Simon Thoresen Hult
 */
public class FlattenTestCase {

    @Test
    public void requireThatHashCodeAndEqualsAreImplemented() {
        Expression exp = new FlattenExpression();
        assertFalse(exp.equals(new Object()));
        assertEquals(exp, new FlattenExpression());
        assertEquals(exp.hashCode(), new FlattenExpression().hashCode());
    }

    @Test
    public void requireThatAnnotationsAreFlattened() {
        SpanTree tree = new SpanTree(SpanTrees.LINGUISTICS);
        tree.annotate(new Span(0, 3), new Annotation(AnnotationTypes.TERM, new StringFieldValue("oof")));
        tree.annotate(new Span(4, 3), new Annotation(AnnotationTypes.TERM, new StringFieldValue("rab")));
        tree.annotate(new Span(8, 3), new Annotation(AnnotationTypes.TERM, new StringFieldValue("zab")));

        StringFieldValue val = new StringFieldValue("foo bar baz");
        val.setSpanTree(tree);

        assertEquals(new StringFieldValue("foo[oof] bar[rab] baz[zab]"), new FlattenExpression().execute(val));
    }

    @Test
    public void requireThatNonTermAnnotationsAreIgnored() {
        SpanTree tree = new SpanTree(SpanTrees.LINGUISTICS);
        tree.annotate(new Span(0, 3), new Annotation(AnnotationTypes.STEM, new StringFieldValue("oof")));

        StringFieldValue val = new StringFieldValue("foo");
        val.setSpanTree(tree);

        assertEquals(new StringFieldValue("foo"), new FlattenExpression().execute(val));
    }

    @Test
    public void requireThatNonSpanAnnotationsAreIgnored() {
        SpanTree tree = new SpanTree(SpanTrees.LINGUISTICS);
        tree.annotate(new Annotation(AnnotationTypes.TERM, new StringFieldValue("oof")));

        StringFieldValue val = new StringFieldValue("foo");
        val.setSpanTree(tree);

        assertEquals(new StringFieldValue("foo"), new FlattenExpression().execute(val));
    }

    @Test
    public void requireThatAnnotationsAreSorted() {
        SpanTree tree = new SpanTree(SpanTrees.LINGUISTICS);
        tree.annotate(new Span(0, 3), new Annotation(AnnotationTypes.TERM, new StringFieldValue("cox")));
        tree.annotate(new Span(0, 3), new Annotation(AnnotationTypes.TERM, new StringFieldValue("baz")));
        tree.annotate(new Span(0, 3), new Annotation(AnnotationTypes.TERM, new StringFieldValue("bar")));

        StringFieldValue val = new StringFieldValue("foo");
        val.setSpanTree(tree);

        assertEquals(new StringFieldValue("foo[bar, baz, cox]"), new FlattenExpression().execute(val));
    }

    @Test
    public void requireThatAnnotationsWithoutFieldValueUseOriginalSpan() {
        SpanTree tree = new SpanTree(SpanTrees.LINGUISTICS);
        tree.annotate(new Span(0, 3), new Annotation(AnnotationTypes.TERM));

        StringFieldValue val = new StringFieldValue("foo");
        val.setSpanTree(tree);

        assertEquals(new StringFieldValue("foo[foo]"), new FlattenExpression().execute(val));
    }

    @Test
    public void requireThatExpressionCanBeVerified() {
        Expression exp = new FlattenExpression();
        assertVerify(DataType.STRING, exp, DataType.STRING);
        assertVerifyThrows(null, exp, "Expected string input, got null.");
        assertVerifyThrows(DataType.INT, exp, "Expected string input, got int.");
    }
}
