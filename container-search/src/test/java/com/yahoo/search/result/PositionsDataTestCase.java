// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.result;

import com.yahoo.data.access.simple.Value;

import static org.junit.Assert.*;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * @author Arne Juul
 */
public class PositionsDataTestCase {

    @Test
    public void testRendering() {
        Value.ArrayValue arr = new Value.ArrayValue();
        Value.ObjectValue p1 = new Value.ObjectValue();
        p1.put("x", new Value.LongValue(-122057174));
        p1.put("y", new Value.LongValue(37374821));
        p1.put("latlong", new Value.StringValue("N37.374821;W122.057174"));
        arr.add(p1);

        PositionsData pd = new PositionsData(arr.inspect());

        String rendered = pd.toXML();
        String correct = "<position x=\"-122057174\" y=\"37374821\" latlong=\"N37.374821;W122.057174\" />";
        assertEquals(correct, rendered);

        rendered = pd.toJson();
        correct = "[{\"x\":-122057174,\"y\":37374821,\"latlong\":\"N37.374821;W122.057174\"}]";
        assertEquals(correct, rendered);

        Value.ObjectValue p2 = new Value.ObjectValue();
        p2.put("x", new Value.LongValue(3));
        p2.put("y", new Value.LongValue(-7));
        p2.put("latlong", new Value.StringValue("S0.000007;E0.000003"));
        arr.add(p2);

        pd = new PositionsData(arr.inspect());

        rendered = pd.toXML();
        correct = "<position x=\"-122057174\" y=\"37374821\" latlong=\"N37.374821;W122.057174\" />" +
                  "<position x=\"3\" y=\"-7\" latlong=\"S0.000007;E0.000003\" />";
        assertEquals(correct, rendered);

        rendered = pd.toJson();
        correct = "[{\"x\":-122057174,\"y\":37374821,\"latlong\":\"N37.374821;W122.057174\"}," +
                  "{\"x\":3,\"y\":-7,\"latlong\":\"S0.000007;E0.000003\"}]";
        assertEquals(correct, rendered);
    }

}
