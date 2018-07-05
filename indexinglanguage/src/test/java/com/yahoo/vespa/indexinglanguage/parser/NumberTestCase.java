// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.parser;

import com.yahoo.document.datatypes.*;
import com.yahoo.vespa.indexinglanguage.expressions.Expression;
import com.yahoo.vespa.indexinglanguage.parser.ParseException;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

/**
 * @author Simon Thoresen Hult
 */
public class NumberTestCase {

    @Test
    public void requireThatCorrectNumberTypeIsParsed() throws ParseException {
        assertTrue(Expression.fromString("6.9").execute() instanceof DoubleFieldValue);
        assertTrue(Expression.fromString("6.9f").execute() instanceof FloatFieldValue);
        assertTrue(Expression.fromString("6.9F").execute() instanceof FloatFieldValue);
        assertTrue(Expression.fromString("69").execute() instanceof IntegerFieldValue);
        assertTrue(Expression.fromString("69l").execute() instanceof LongFieldValue);
        assertTrue(Expression.fromString("69L").execute() instanceof LongFieldValue);
        assertTrue(Expression.fromString("0x69").execute() instanceof IntegerFieldValue);
        assertTrue(Expression.fromString("0x69l").execute() instanceof LongFieldValue);
        assertTrue(Expression.fromString("0x69L").execute() instanceof LongFieldValue);
        assertTrue(Expression.fromString("'69'").execute() instanceof StringFieldValue);
        assertTrue(Expression.fromString("\"69\"").execute() instanceof StringFieldValue);
    }
}
