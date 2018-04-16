// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.context.test;

import com.yahoo.search.Query;
import com.yahoo.search.query.context.QueryContext;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * @author bratseth
 */
public class PropertiesTestCase {

    @Test
    public void testProperties() {
        Query query=new Query();
        QueryContext h = query.getContext(true);
        h.setProperty("a","a1");
        h.trace("first message", 2);
        h.setProperty("a","a2");
        h.setProperty("b","b1");
        query.clone();
        QueryContext h2 = query.clone().getContext(true);
        h2.setProperty("b","b2");
        h2.trace("second message", 2);
        h2.setProperty("b","b3");
        h.setProperty("b","b4");
        QueryContext h3 = query.clone().getContext(true);
        h3.setProperty("b","b5");
        h3.setProperty("c","c1");
        h3.trace("third message", 2);
        h2.setProperty("c","c2");
        h.trace("fourth message", 2);
        h.setProperty("d","d1");
        h2.trace("fifth message", 2);
        h2.setProperty("c","c3");
        h.setProperty("c","c4");

        assertEquals("a2",h.getProperty("a"));
        assertEquals("b5",h.getProperty("b"));
        assertEquals("c4",h.getProperty("c"));
        assertEquals("d1",h.getProperty("d"));
        assertNull(h.getProperty("e"));
    }

}
