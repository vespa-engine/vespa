// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.collections;

import org.junit.Test;
import static org.junit.Assert.fail;

/**
 * @author bratseth
 */
public class FreezableArrayListListener {

    @Test
    public void testPermitAdd() {
        FreezableArrayList<String> l = new FreezableArrayList<>(true);
        l.add("1");
        l.add("2");
        l.remove(1);
        l.freeze();
        try {
            l.remove(0);
            fail("Expected exception");
        }
        catch (UnsupportedOperationException expected) {
        }
        try {
            l.set(0, "2");
            fail("Expected exception");
        }
        catch (UnsupportedOperationException expected) {
        }
        try {
            l.add(0, "2");
            fail("Expected exception");
        }
        catch (UnsupportedOperationException expected) {
        }

        l.add("2");
    }

    @Test
    public void testDontPermitAdd() {
        FreezableArrayList<String> l = new FreezableArrayList<>();
        l.add("1");
        l.add("2");
        l.remove(1);
        l.freeze();
        try {
            l.remove(0);
            fail("Expected exception");
        }
        catch (UnsupportedOperationException expected) {
        }
        try {
            l.set(0, "2");
            fail("Expected exception");
        }
        catch (UnsupportedOperationException expected) {
        }
        try {
            l.add(0, "2");
            fail("Expected exception");
        }
        catch (UnsupportedOperationException expected) {
        }
        try {
            l.add("2");
            fail("Expected exception");
        }
        catch (UnsupportedOperationException expected) {
        }
    }

}
