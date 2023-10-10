// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.collections;

import static org.junit.Assert.*;

import org.junit.Test;

/**
 * Test case used for testing and experimenting with the tuple APIs. It seems
 * Tuple4 is just as horrible as I first assumed, but using quick-fix funtions
 * in the IDE made writing the code less painful than I guessed..
 *
 * @author <a href="mailto:steinar@yahoo-inc.com">Steinar Knutsen</a>
 */
public class TupleTestCase {

    private static final String _12 = "12";
    private static final Integer _11 = Integer.valueOf(11);

    Tuple2<Integer, String> instance = new Tuple2<>(_11, _12);


    @Test
    public final void objectStuff() {
        boolean hashException = false;
        boolean equalsException = false;
        assertEquals("Tuple2(11, 12)", instance.toString());
        try {
            instance.hashCode();
        } catch (UnsupportedOperationException e) {
            hashException = true;
        }
        assertTrue(hashException);
        try {
            instance.equals(null);
        } catch (UnsupportedOperationException e) {
            equalsException = true;
        }
        assertTrue(equalsException);
    }

    @Test
    public final void basicUse() {
        assertSame(_11, instance.first);
        assertSame(_12, instance.second);
    }

}
