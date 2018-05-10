// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.fastsearch;

import static org.junit.Assert.*;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.yahoo.data.access.*;
import com.yahoo.data.access.simple.*;

public class JsonFieldTestCase {

    @Test
    public final void requireThatWeightedSetsItemsAreConvertedToStrings() {
        Value.ArrayValue topArr = new Value.ArrayValue();
        topArr.add(new Value.ArrayValue()
                   .add(new Value.DoubleValue(17.5))
                   .add(new Value.LongValue(10)));
        topArr.add(new Value.ArrayValue()
                   .add(new Value.DoubleValue(0.25))
                   .add(new Value.DoubleValue(20)));

        Inspector c = JSONField.convertTop(topArr);

        assertEquals(Type.STRING, c.entry(0).entry(0).type());
        assertEquals(Type.LONG,   c.entry(0).entry(1).type());
        assertEquals(Type.STRING, c.entry(1).entry(0).type());
        assertEquals(Type.DOUBLE, c.entry(1).entry(1).type());

        assertEquals("17.5", c.entry(0).entry(0).asString());
        assertEquals(10,     c.entry(0).entry(1).asLong());
        assertEquals("0.25", c.entry(1).entry(0).asString());
        assertEquals(20.0,   c.entry(1).entry(1).asDouble(), 0.01);
    }

    @Test
    public final void requireThatNewWeightedSetsAreConvertedToOldFormat() {
        Value.ArrayValue topArr = new Value.ArrayValue();
        topArr.add(new Value.ObjectValue()
                   .put("item", new Value.DoubleValue(17.5))
                   .put("weight", new Value.LongValue(10)));
        topArr.add(new Value.ObjectValue()
                   .put("item", new Value.DoubleValue(0.25))
                   .put("weight", new Value.DoubleValue(20)));
        topArr.add(new Value.ObjectValue()
                   .put("item", new Value.StringValue("foob"))
                   .put("weight", new Value.DoubleValue(30)));

        Inspector c = JSONField.convertTop(topArr);

        assertEquals(Type.STRING, c.entry(0).entry(0).type());
        assertEquals(Type.LONG,   c.entry(0).entry(1).type());
        assertEquals(Type.STRING, c.entry(1).entry(0).type());
        assertEquals(Type.DOUBLE, c.entry(1).entry(1).type());
        assertEquals(Type.STRING, c.entry(2).entry(0).type());
        assertEquals(Type.DOUBLE, c.entry(2).entry(1).type());

        assertEquals("17.5", c.entry(0).entry(0).asString());
        assertEquals(10,     c.entry(0).entry(1).asLong());
        assertEquals("0.25", c.entry(1).entry(0).asString());
        assertEquals(20.0,   c.entry(1).entry(1).asDouble(), 0.01);
        assertEquals("foob", c.entry(2).entry(0).asString());
        assertEquals(30.0,   c.entry(2).entry(1).asDouble(), 0.01);
    }

    @Test
    public final void requireThatArrayValuesAreConvertedToStrings() {
        Value.ArrayValue topArr = new Value.ArrayValue();
        topArr.add(new Value.DoubleValue(17.5));
        topArr.add(new Value.DoubleValue(0.25));
        topArr.add(new Value.LongValue(10));
        topArr.add(new Value.DoubleValue(20));

        Inspector c = JSONField.convertTop(topArr);

        assertEquals(Type.STRING, c.entry(0).type());
        assertEquals(Type.STRING, c.entry(1).type());
        assertEquals(Type.STRING, c.entry(2).type());
        assertEquals(Type.STRING, c.entry(3).type());

        assertEquals("17.5", c.entry(0).asString());
        assertEquals("0.25", c.entry(1).asString());
        assertEquals("10",   c.entry(2).asString());
        assertEquals("20.0", c.entry(3).asString());
    }

}
