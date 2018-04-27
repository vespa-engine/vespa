// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus;

import com.yahoo.messagebus.test.SimpleMessage;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class RateThrottlingTestCase {

    @Test
    public void testPending() {
        CustomTimer timer = new CustomTimer();
        RateThrottlingPolicy policy = new RateThrottlingPolicy(5.0, timer);
        policy.setMaxPendingCount(200);

        // Check that we obey the max still.
        assertFalse(policy.canSend(new SimpleMessage("test"), 300));
    }

    public int getActualRate(double desiredRate) {
        CustomTimer timer = new CustomTimer();
        RateThrottlingPolicy policy = new RateThrottlingPolicy(desiredRate, timer);

        int ok = 0;
        for (int i = 0; i < 10000; ++i) {
            if (policy.canSend(new SimpleMessage("test"), 0)) {
                ok++;
            }
            timer.millis += 10;
        }

        return ok;
    }

    @Test
    public void testRates() {
        assertEquals(10, getActualRate(0.1), 1);
        assertEquals(1000, getActualRate(10), 100);
        assertEquals(500, getActualRate(5), 50);
        assertEquals(100, getActualRate(1), 10);
    }

}
