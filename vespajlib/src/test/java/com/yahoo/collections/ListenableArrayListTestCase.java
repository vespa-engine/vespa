// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.collections;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.ListIterator;

/**
 * @author bratseth
 */
public class ListenableArrayListTestCase {

    @Test
    public void testIt() {
        ListenableArrayList<String> list = new ListenableArrayList<>();
        ArrayListListener listener = new ArrayListListener();
        list.addListener(listener);
        assertEquals(0,listener.invoked);
        list.add("a");
        assertEquals(1,listener.invoked);
        list.add(0,"b");
        assertEquals(2,listener.invoked);
        list.addAll(Arrays.asList(new String[]{"c", "d"}));
        assertEquals(3,listener.invoked);
        list.addAll(1,Arrays.asList(new String[]{"e", "f"}));
        assertEquals(4,listener.invoked);
        list.set(0,"g");
        assertEquals(5,listener.invoked);
        ListIterator<String> i = list.listIterator();
        i.add("h");
        assertEquals(6,listener.invoked);
        i.next();
        i.set("i");
        assertEquals(7,listener.invoked);
    }

    private static class ArrayListListener implements Runnable {

        int invoked;

        @Override
        public void run() {
            invoked++;
        }

    }

}
