// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.processing.request.test;

import com.yahoo.processing.Request;
import com.yahoo.processing.request.CompoundName;
import com.yahoo.processing.request.ErrorMessage;
import com.yahoo.processing.request.Properties;
import com.yahoo.processing.request.properties.PropertyMap;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * Tests using requests
 *
 * @author bratseth
 */
public class RequestTestCase {

    private static final double delta = 0.0000000001;

    @Test
    public void testProperties() {
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
        assertEquals("b1", r.properties().get(new CompoundName("b")));
        assertEquals("b1", r.properties().get(new CompoundName("b"), "default"));
        assertEquals("default", r.properties().get(new CompoundName("c"), "default"));
        assertNull(r.properties().get(new CompoundName("c")));

        assertEquals("b1", r.properties().getString("b"));
        assertEquals("b1", r.properties().getString("b","default"));
        assertEquals("default", r.properties().getString("c","default"));
        assertEquals(null, r.properties().getString("c"));
        assertEquals("b1", r.properties().getString(new CompoundName("b")));
        assertEquals("b1", r.properties().getString(new CompoundName("b"),"default"));
        assertEquals("default", r.properties().getString(new CompoundName("c"),"default"));
        assertEquals(null, r.properties().getString(new CompoundName("c")));

        r.properties().set("i",7);
        assertEquals(7, (int)r.properties().getInteger("i"));
        assertEquals(7, (int)r.properties().getInteger("i",3));
        assertEquals(3, (int)r.properties().getInteger("n",3));
        assertNull(r.properties().getInteger("n"));
        assertEquals(7, (int)r.properties().getInteger(new CompoundName("i")));
        assertEquals(7, (int)r.properties().getInteger(new CompoundName("i"),3));
        assertEquals(3, (int)r.properties().getInteger(new CompoundName("n"),3));
        assertNull(r.properties().getInteger("n"));

        r.properties().set(new CompoundName("l"), 7);
        assertEquals(7, (long) r.properties().getLong("l"));
        assertEquals(7, (long)r.properties().getLong("l",3l));
        assertEquals(3, (long)r.properties().getLong("m",3l));
        assertNull(r.properties().getInteger("m"));
        assertEquals(7, (long)r.properties().getLong(new CompoundName("l")));
        assertEquals(7, (long)r.properties().getLong(new CompoundName("l"),3l));
        assertEquals(3, (long)r.properties().getLong(new CompoundName("m"),3l));
        assertNull(r.properties().getInteger("m"));

        r.properties().set("d", 7.3);
        assertEquals(7.3, r.properties().getDouble("d"), delta);
        assertEquals(7.3, r.properties().getDouble("d",3.4d), delta);
        assertEquals(3.4, r.properties().getDouble("f",3.4d), delta);
        assertNull(r.properties().getDouble("f"));
        assertEquals(7.3, r.properties().getDouble(new CompoundName("d")), delta);
        assertEquals(7.3, r.properties().getDouble(new CompoundName("d"),3.4d), delta);
        assertEquals(3.4, r.properties().getDouble(new CompoundName("f"),3.4d), delta);
        assertNull(r.properties().getDouble("f"));

        r.properties().set("o",true);
        assertEquals(true, r.properties().getBoolean("o"));
        assertEquals(true, r.properties().getBoolean("o",true));
        assertEquals(true, r.properties().getBoolean("g",true));
        assertEquals(false, r.properties().getBoolean("g"));
        assertEquals(true, r.properties().getBoolean(new CompoundName("o")));
        assertEquals(true, r.properties().getBoolean(new CompoundName("o"),true));
        assertEquals(true, r.properties().getBoolean(new CompoundName("g"),true));
        assertEquals(false, r.properties().getBoolean("g"));

        r.properties().set(new CompoundName("x.y"), "x1.y1");
        r.properties().set("x.z", "x1.z1");

        assertEquals(8, r.properties().listProperties().size());
        assertEquals(0, r.properties().listProperties("a").size());
        assertEquals(0, r.properties().listProperties(new CompoundName("a")).size());
        assertEquals(0, r.properties().listProperties(new CompoundName("none")).size());
        assertEquals(2, r.properties().listProperties(new CompoundName("x")).size());
        assertEquals(2, r.properties().listProperties("x").size());
    }

    @Test
    public void testErrorMessages() {
        Request r = new Request();
        r.errors().add(new ErrorMessage("foo"));
        r.errors().add(new ErrorMessage("bar"));
        assertEquals(2,r.errors().size());
        assertEquals("foo", r.errors().get(0).getMessage());
        assertEquals("bar", r.errors().get(1).getMessage());
    }

    @Test
    public void testCloning() {
        Request request = new Request();
        request.properties().set("a","a1");
        request.properties().set("b","b1");
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
        assertEquals(null, request.properties().get("c"));
        assertEquals("c1", rcloned.properties().get("c"));
        assertEquals("d1", request.properties().get("d"));
        assertEquals(null, rcloned.properties().get("d"));

        assertEquals(3, request.errors().size());
        assertEquals(1, rcloned.errors().size());
        assertEquals("foo",request.errors().get(0).getMessage());
        assertEquals("bar",request.errors().get(1).getMessage());
        assertEquals("boz",request.errors().get(2).getMessage());
        assertEquals("baz",rcloned.errors().get(0).getMessage());
    }

}
