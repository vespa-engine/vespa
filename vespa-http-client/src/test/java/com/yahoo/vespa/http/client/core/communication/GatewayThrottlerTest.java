// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.client.core.communication;

import org.junit.Before;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;


public class GatewayThrottlerTest {

    GatewayThrottler gatewayThrottler;
    long lastSleepValue = 0;

    @Before
    public void before() {
        gatewayThrottler = new GatewayThrottler(900) {
            @Override
            protected void sleepMs(long sleepTime) {
                lastSleepValue = sleepTime;
            }
        };
    }

    @Test
    public void noSleepOnNormalCase() {
        gatewayThrottler.handleCall(0);
        gatewayThrottler.handleCall(0);
        assertThat(lastSleepValue, is(0L));
    }

    @Test
    public void increastingSleepTime() {
        gatewayThrottler.handleCall(1);
        long sleepTime1 = lastSleepValue;
        gatewayThrottler.handleCall(1);
        long sleepTime2 = lastSleepValue;
        assertTrue(sleepTime1 > 0);
        assertTrue(sleepTime2 > sleepTime1);
        int x;
        // Check for max value of sleep time.
        for (x = 0 ; x < 10000; x++) {
            long prevSleepTime = lastSleepValue;
            gatewayThrottler.handleCall(1);
            if (prevSleepTime == lastSleepValue) break;
        }
        assertTrue(x < 5000);
        // Check that it goes down back to zero when no errors.
        for (x = 0 ; x < 10000; x++) {
            gatewayThrottler.handleCall(0);
            if (lastSleepValue == 0) break;
        }
        assertTrue(x < 5000);
    }
}
