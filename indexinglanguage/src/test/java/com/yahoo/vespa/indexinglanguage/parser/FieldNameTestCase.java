// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.parser;

import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.vespa.indexinglanguage.expressions.*;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author Simon Thoresen Hult
 */
public class FieldNameTestCase {

    @Test
    public void requireThatDotCanBeUsedInFieldName() throws ParseException {
        assertEquals(new AttributeExpression("foo.bar"), Expression.fromString("attribute foo . bar"));
        assertEquals(new IndexExpression("foo.bar"), Expression.fromString("index foo . bar"));
        assertEquals(new SummaryExpression("foo.bar"), Expression.fromString("summary foo . bar"));
    }

    @Test
    public void requireThatCatDotIsNotConfusedWithFieldName() throws ParseException {
        assertEquals(new CatExpression(new InputExpression("foo"), new InputExpression("bar")),
                     Expression.fromString("input foo . input bar"));
        assertEquals(new CatExpression(new InputExpression("foo"), new SetValueExpression(new StringFieldValue("bar"))),
                     Expression.fromString("input foo . 'bar'"));
    }
}
