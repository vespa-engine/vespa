// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.result;

import com.yahoo.search.grouping.Continuation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * @author Simon Thoresen Hult
 */
public class HitListTestCase {

    @Test
    void requireThatAccessorsWork() {
        HitList lst = new HitList("foo");
        assertEquals("foo", lst.getLabel());
        assertEquals(0, lst.continuations().size());

        MyContinuation foo = new MyContinuation();
        lst.continuations().put("foo", foo);
        assertEquals(1, lst.continuations().size());
        assertSame(foo, lst.continuations().get("foo"));

        MyContinuation bar = new MyContinuation();
        lst.continuations().put("bar", bar);
        assertEquals(2, lst.continuations().size());
        assertSame(bar, lst.continuations().get("bar"));
    }

    private static class MyContinuation extends Continuation {

        @Override
        public Continuation copy() {
            return null;
        }

    }

}
