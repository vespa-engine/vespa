// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.language.process;

import static org.junit.Assert.*;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Functional testing of StemList.
 *
 * @author Steinar Knutsen
 */
public class StemListTestCase {

    private StemList stems;

    @Before
    public void setUp() throws Exception {
        stems = new StemList();
    }

    @After
    public void tearDown() throws Exception {
        stems = null;
    }

    @Test
    public void testSize() {
        assertEquals(0, stems.size());
        stems.add("a");
        stems.add("b");
        stems.add("a");
        assertEquals(2, stems.size());
    }

    @Test
    public void testSet() {
        stems.add("a");
        stems.add("b");
        stems.add("c");
        stems.add("d");
        assertEquals("a", stems.set(2, "a"));
        assertEquals("c", stems.get(2));
        assertEquals("c", stems.set(2, "z"));
        assertEquals("z", stems.get(2));
    }

    @Test
    public void testAdd() {
        stems.add("a");
        stems.add("b");
        stems.add("c");
        stems.add("d");
        assertEquals(4, stems.size());
        stems.add("a");
        assertEquals(4, stems.size());
        stems.add("z");
        assertEquals(5, stems.size());
    }

    @Test
    public void testremove() {
        stems.add("a");
        stems.add("b");
        stems.add("c");
        stems.add("d");
        assertEquals("c", stems.remove(2));
        assertEquals(3, stems.size());
    }

}
