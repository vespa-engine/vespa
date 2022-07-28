// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.result;

import com.yahoo.data.access.simple.Value;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Arne Juul
 */
public class PositionsDataTestCase {

    @Test
    void testRenderingOfSinglePosition() {
        Value.ObjectValue pos = createPosition(-122057174, 37374821, "N37.374821;W122.057174");

        PositionsData pd = new PositionsData(pos.inspect());

        assertXml("<position x=\"-122057174\" y=\"37374821\" latlong=\"N37.374821;W122.057174\" />", pd);
        assertJson("{\"x\":-122057174,\"y\":37374821,\"latlong\":\"N37.374821;W122.057174\"}", pd);
    }

    @Test
    void testRenderingOfMultiplePositions() {
        Value.ArrayValue arr = new Value.ArrayValue();
        arr.add(createPosition(-122057174, 37374821, "N37.374821;W122.057174"));
        arr.add(createPosition(3, -7, "S0.000007;E0.000003"));

        PositionsData pd = new PositionsData(arr.inspect());

        assertXml("<position x=\"-122057174\" y=\"37374821\" latlong=\"N37.374821;W122.057174\" />" +
                "<position x=\"3\" y=\"-7\" latlong=\"S0.000007;E0.000003\" />", pd);
        assertJson("[{\"x\":-122057174,\"y\":37374821,\"latlong\":\"N37.374821;W122.057174\"}," +
                "{\"x\":3,\"y\":-7,\"latlong\":\"S0.000007;E0.000003\"}]", pd);
    }

    private Value.ObjectValue createPosition(long x, long y, String latlong) {
        Value.ObjectValue result = new Value.ObjectValue();
        result.put("x", new Value.LongValue(x));
        result.put("y", new Value.LongValue(y));
        result.put("latlong", new Value.StringValue(latlong));
        return result;
    }

    private void assertXml(String expected, PositionsData pd) {
        assertEquals(expected, pd.toXML());
    }

    private void assertJson(String expected, PositionsData pd) {
        assertEquals(expected, pd.toJson());
    }

}
