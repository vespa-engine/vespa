// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.utils.test;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class FakeClockTest {

    @Test
    public void testSimple() {
        FakeClock clock = new FakeClock();
            // Should not start at 0, as that is common not initialized yet value
        assertTrue(clock.getTimeInMillis() > 0);
        long start = clock.getTimeInMillis();

        clock.adjust(5);
        assertEquals(start + 5, clock.getTimeInMillis());

        clock.set(start + 10);
        assertEquals(start + 10, clock.getTimeInMillis());

        clock.adjust(5);
        assertEquals(start + 15, clock.getTimeInMillis());
    }

    // TODO: This should probably throw exceptions.. However, that doesn't seem to be current behavior.
    // I suspect some tests misuse the clock to reset things to run another test. Should probably be fixed.
    @Test
    public void testTurnTimeBack() {
        FakeClock clock = new FakeClock();
        clock.set(1000);

        clock.set(500);
        assertEquals(500, clock.getTimeInMillis());

        clock.adjust(-100);
        assertEquals(400, clock.getTimeInMillis());
    }

}
