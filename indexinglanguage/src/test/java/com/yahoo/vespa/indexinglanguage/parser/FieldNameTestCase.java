// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.parser;

import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.vespa.indexinglanguage.expressions.AttributeExpression;
import com.yahoo.vespa.indexinglanguage.expressions.CatExpression;
import com.yahoo.vespa.indexinglanguage.expressions.ConstantExpression;
import com.yahoo.vespa.indexinglanguage.expressions.Expression;
import com.yahoo.vespa.indexinglanguage.expressions.IndexExpression;
import com.yahoo.vespa.indexinglanguage.expressions.InputExpression;
import com.yahoo.vespa.indexinglanguage.expressions.SummaryExpression;
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
        assertEquals(new CatExpression(new InputExpression("foo"), new ConstantExpression(new StringFieldValue("bar"))),
                     Expression.fromString("input foo . 'bar'"));
    }

    @Test
    public void requireThatNonIdentifierOutputFieldNamesRoundTripThroughQuoting() throws ParseException {
        // Names containing characters outside the identifier charset, such as the synthetic
        // 'mymap$keyvalue' attribute, must be quoted when serialized to be re-parseable.
        assertEquals("attribute \"mymap$keyvalue\"", new AttributeExpression("mymap$keyvalue").toString());
        assertEquals(new AttributeExpression("mymap$keyvalue"), Expression.fromString("attribute \"mymap$keyvalue\""));
        assertEquals(new AttributeExpression("mymap$keyvalue"),
                     Expression.fromString(new AttributeExpression("mymap$keyvalue").toString()));
        assertEquals(new IndexExpression("mymap$keyvalue"),
                     Expression.fromString(new IndexExpression("mymap$keyvalue").toString()));
        assertEquals(new SummaryExpression("mymap$keyvalue"),
                     Expression.fromString(new SummaryExpression("mymap$keyvalue").toString()));
        // Identifier and dotted names must stay unquoted, so existing configs serialize unchanged.
        assertEquals("attribute foo", new AttributeExpression("foo").toString());
        assertEquals("attribute foo.bar", new AttributeExpression("foo.bar").toString());
    }
}
