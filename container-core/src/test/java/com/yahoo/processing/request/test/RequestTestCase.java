// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.processing.request.test;

import com.yahoo.processing.Request;
import com.yahoo.processing.request.CompoundName;
import com.yahoo.processing.request.ErrorMessage;
import com.yahoo.processing.request.Properties;
import com.yahoo.processing.request.properties.PropertyMap;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests using requests
 *
 * @author bratseth
 */
public class RequestTestCase {

    private static final double delta = 0.0000000001;
    private static final CompoundName C_a = CompoundName.from("a");

    private static final CompoundName C_B = CompoundName.from("b");
    private static final CompoundName C_C = CompoundName.from("c");
    private static final CompoundName C_D = CompoundName.from("d");
    private static final CompoundName C_f = CompoundName.from("f");
    private static final CompoundName C_g = CompoundName.from("g");
    private static final CompoundName C_I = CompoundName.from("i");
    private static final CompoundName C_L = CompoundName.from("l");
    private static final CompoundName C_M = CompoundName.from("m");
    private static final CompoundName C_N = CompoundName.from("n");
    private static final CompoundName C_o = CompoundName.from("o");
    private static final CompoundName C_x = CompoundName.from("x");
    private static final CompoundName C_none = CompoundName.from("none");



    @Test
    void testProperties() {

        Properties p = new PropertyMap();
        p.set("a", "a1");
        Request r = new Request(p);
        r.properties().set("b", "b1");
        assertEquals(2, r.properties().listProperties().size());
        assertEquals("a1", r.properties().get("a"));

        assertEquals("b1", r.properties().get("b"));
        assertEquals("b1", r.properties().get("b", "default"));
        assertEquals("default", r.properties().get("c", "default"));
        assertNull(r.properties().get("c"));
        assertEquals("b1", r.properties().get(C_B));
        assertEquals("b1", r.properties().get(C_B, "default"));
        assertEquals("default", r.properties().get(C_C, "default"));
        assertNull(r.properties().get(C_C));

        assertEquals("b1", r.properties().getString("b"));
        assertEquals("b1", r.properties().getString("b", "default"));
        assertEquals("default", r.properties().getString("c", "default"));
        assertNull(r.properties().getString("c"));
        assertEquals("b1", r.properties().getString(C_B));
        assertEquals("b1", r.properties().getString(C_B, "default"));
        assertEquals("default", r.properties().getString(C_C, "default"));
        assertNull(r.properties().getString(C_C));

        r.properties().set("i", 7);
        assertEquals(7, (int) r.properties().getInteger("i"));
        assertEquals(7, (int) r.properties().getInteger("i", 3));
        assertEquals(3, (int) r.properties().getInteger("n", 3));
        assertNull(r.properties().getInteger("n"));
        assertEquals(7, (int) r.properties().getInteger(C_I));
        assertEquals(7, (int) r.properties().getInteger(C_I, 3));
        assertEquals(3, (int) r.properties().getInteger(C_N, 3));
        assertNull(r.properties().getInteger("n"));

        r.properties().set(C_L, 7);
        assertEquals(7, (long) r.properties().getLong("l"));
        assertEquals(7, (long) r.properties().getLong("l", 3L));
        assertEquals(3, (long) r.properties().getLong("m", 3L));
        assertNull(r.properties().getInteger("m"));
        assertEquals(7, (long) r.properties().getLong(C_L));
        assertEquals(7, (long) r.properties().getLong(C_L, 3L));
        assertEquals(3, (long) r.properties().getLong(C_M, 3L));
        assertNull(r.properties().getInteger("m"));

        r.properties().set("d", 7.3);
        assertEquals(7.3, r.properties().getDouble("d"), delta);
        assertEquals(7.3, r.properties().getDouble("d", 3.4d), delta);
        assertEquals(3.4, r.properties().getDouble("f", 3.4d), delta);
        assertNull(r.properties().getDouble("f"));
        assertEquals(7.3, r.properties().getDouble(C_D), delta);
        assertEquals(7.3, r.properties().getDouble(C_D, 3.4d), delta);
        assertEquals(3.4, r.properties().getDouble(C_f, 3.4d), delta);
        assertNull(r.properties().getDouble("f"));

        r.properties().set("o", true);
        assertTrue(r.properties().getBoolean("o"));
        assertTrue(r.properties().getBoolean("o", true));
        assertTrue(r.properties().getBoolean("g", true));
        assertFalse(r.properties().getBoolean("g"));
        assertTrue(r.properties().getBoolean(C_o));
        assertTrue(r.properties().getBoolean(C_o, true));
        assertTrue(r.properties().getBoolean(C_g, true));
        assertFalse(r.properties().getBoolean("g"));

        r.properties().set(CompoundName.from("x.y"), "x1.y1");
        r.properties().set("x.z", "x1.z1");

        assertEquals(8, r.properties().listProperties().size());
        assertEquals(0, r.properties().listProperties("a").size());
        assertEquals(0, r.properties().listProperties(C_a).size());
        assertEquals(0, r.properties().listProperties(C_none).size());
        assertEquals(2, r.properties().listProperties(C_x).size());
        assertEquals(2, r.properties().listProperties("x").size());
    }

    @Test
    void testErrorMessages() {
        Request r = new Request();
        r.errors().add(new ErrorMessage("foo"));
        r.errors().add(new ErrorMessage("bar"));
        assertEquals(2, r.errors().size());
        assertEquals("foo", r.errors().get(0).getMessage());
        assertEquals("bar", r.errors().get(1).getMessage());
    }

    @Test
    void testCloning() {
        Request request = new Request();
        request.properties().set("a", "a1");
        request.properties().set("b", "b1");
        request.errors().add(new ErrorMessage("foo"));
        request.errors().add(new ErrorMessage("bar"));
        Request rcloned = request.clone();
        rcloned.properties().set("c", "c1");
        rcloned.errors().add(new ErrorMessage("baz"));
        request.properties().set("d", "d1");
        request.errors().add(new ErrorMessage("boz"));

        assertEquals("a1", request.properties().get("a"));
        assertEquals("a1", rcloned.properties().get("a"));
        assertEquals("b1", request.properties().get("b"));
        assertEquals("b1", rcloned.properties().get("b"));
        assertNull(request.properties().get("c"));
        assertEquals("c1", rcloned.properties().get("c"));
        assertEquals("d1", request.properties().get("d"));
        assertNull(rcloned.properties().get("d"));

        assertEquals(3, request.errors().size());
        assertEquals(1, rcloned.errors().size());
        assertEquals("foo", request.errors().get(0).getMessage());
        assertEquals("bar", request.errors().get(1).getMessage());
        assertEquals("boz", request.errors().get(2).getMessage());
        assertEquals("baz", rcloned.errors().get(0).getMessage());
    }

}
