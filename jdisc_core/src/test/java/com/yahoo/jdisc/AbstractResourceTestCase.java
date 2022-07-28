// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author Simon Thoresen Hult
 */
public class AbstractResourceTestCase {

    @Test
    void requireThatDestroyIsCalledWhenReleased() {
        MyResource res = new MyResource();
        assertFalse(res.destroyed);
        res.release();
        assertTrue(res.destroyed);
    }

    @Test
    void requireThatDestroyIsCalledWhenRetainCountReachesZero() {
        MyResource res = new MyResource();
        assertEquals(1, res.retainCount());
        assertFalse(res.destroyed);
        final ResourceReference reference = res.refer();
        assertEquals(2, res.retainCount());
        res.release();
        assertEquals(1, res.retainCount());
        assertFalse(res.destroyed);
        reference.close();
        assertEquals(0, res.retainCount());
        assertTrue(res.destroyed);
    }

    @Test
    void requireThatDestroyIsCalledWhenRetainCountReachesZeroOppositeOrder() {
        MyResource res = new MyResource();
        assertEquals(1, res.retainCount());
        assertFalse(res.destroyed);
        final ResourceReference reference = res.refer();
        assertEquals(2, res.retainCount());
        reference.close();
        assertEquals(1, res.retainCount());
        assertFalse(res.destroyed);
        res.release();
        assertEquals(0, res.retainCount());
        assertTrue(res.destroyed);
    }

    @Test
    void requireThatReleaseCanOnlyBeCalledOnceEvenWhenReferenceCountIsPositive() {
        MyResource res = new MyResource();
        final ResourceReference secondReference = res.refer();
        res.release();
        try {
            res.release();
            fail();
        } catch (IllegalStateException e) {
            // As expected.
        }
        secondReference.close();
    }

    @Test
    void requireThatSecondaryReferenceCanOnlyBeClosedOnceEvenWhenReferenceCountIsPositive() {
        MyResource res = new MyResource();
        final ResourceReference secondReference = res.refer();
        secondReference.close();
        try {
            secondReference.close();
            fail();
        } catch (IllegalStateException e) {
            // As expected.
        }
        res.release();
    }

    @Test
    void requireThatReleaseAfterDestroyThrows() {
        MyResource res = new MyResource();
        res.release();
        assertTrue(res.destroyed);
        try {
            res.release();
            fail();
        } catch (IllegalStateException e) {

        }
        assertEquals(0, res.retainCount());
        try {
            res.release();
            fail();
        } catch (IllegalStateException e) {

        }
        assertEquals(0, res.retainCount());
    }

    @Test
    void requireThatReferAfterDestroyThrows() {
        MyResource res = new MyResource();
        res.release();
        assertTrue(res.destroyed);
        try {
            res.refer();
            fail();
        } catch (IllegalStateException e) {

        }
        assertEquals(0, res.retainCount());
        try {
            res.refer();
            fail();
        } catch (IllegalStateException e) {

        }
        assertEquals(0, res.retainCount());
    }

    private static class MyResource extends AbstractResource {

        boolean destroyed = false;

        @Override
        protected void destroy() {
            destroyed = true;
        }
    }
}
