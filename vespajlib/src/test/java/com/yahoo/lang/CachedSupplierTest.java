// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.lang;

import com.yahoo.test.ManualClock;
import org.junit.Test;

import java.time.Duration;

import static org.junit.Assert.assertEquals;

/**
 * @author freva
 */
public class CachedSupplierTest {

    @Test
    public void test() {
        ManualClock clock = new ManualClock();
        MutableInteger integer = new MutableInteger(0);
        CachedSupplier<Integer> supplier = new CachedSupplier<>(() -> integer.add(1), Duration.ofMinutes(1), clock);

        assertEquals(1, supplier.get().intValue());
        assertEquals(1, supplier.get().intValue());

        clock.advance(Duration.ofSeconds(30));
        assertEquals(1, supplier.get().intValue());

        clock.advance(Duration.ofSeconds(31));
        assertEquals(2, supplier.get().intValue());
        assertEquals(2, supplier.get().intValue());

        supplier.invalidate();
        assertEquals(3, supplier.get().intValue());
        assertEquals(3, supplier.get().intValue());

        clock.advance(Duration.ofSeconds(61));
        assertEquals(4, supplier.get().intValue());
    }
}
