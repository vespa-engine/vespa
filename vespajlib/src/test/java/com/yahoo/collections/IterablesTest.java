// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.collections;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author jonmv
 */
class IterablesTest {

    @Test
    void testEmpty() {
        List<Integer> elements = new ArrayList<>();
        Iterator<Integer> iterator = Iterables.reversed(elements).iterator();
        assertFalse(iterator.hasNext());
        assertThrows(NoSuchElementException.class, iterator::next);
        assertThrows(IllegalStateException.class, iterator::remove);
    }

    @Test
    void testIterator() {
        List<Integer> elements = new ArrayList<>(List.of(1, 2, 3));
        Iterator<Integer> iterator = Iterables.reversed(elements).iterator();
        assertTrue(iterator.hasNext());
        assertEquals(3, iterator.next());
        iterator.remove();
        assertThrows(IllegalStateException.class, iterator::remove);
        assertTrue(iterator.hasNext());
        assertEquals(2, iterator.next());
        assertTrue(iterator.hasNext());
        assertEquals(1, iterator.next());
        iterator.remove();
        assertThrows(IllegalStateException.class, iterator::remove);
        assertThrows(NoSuchElementException.class, iterator::next);
        assertEquals(List.of(2), elements);
    }

}
