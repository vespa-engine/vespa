// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.client.core;

import org.junit.Test;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.*;

public class ThrottlePolicyTest {

    private final ThrottlePolicy throttlePolicy = new ThrottlePolicy();
    // Default values for tests.
    private int numOk = 1000;
    private int prevOk = 1000;
    private int prevMax = 100;
    private int max = 100;
    private boolean queued = true;
    private double dynamicFactor = 0.1;

    @Test
    public void samePerformanceShouldTuneDown() {
        assertThat(throttlePolicy.calcNewMaxInFlight(dynamicFactor, numOk, prevOk, prevMax, max, queued), is(95));
    }

    @Test
    public void improvedPerformanceSameSizeShouldTuneDown() {
        numOk += 200;
        assertThat(throttlePolicy.calcNewMaxInFlight(dynamicFactor, numOk, prevOk, prevMax, max, queued), is(89));
    }

    @Test
    public void improvedPerformanceSmallerSizeTuneDownFurther() {
        numOk += 200;
        max = 70;
        assertThat(throttlePolicy.calcNewMaxInFlight(dynamicFactor, numOk, prevOk, prevMax, max, queued), is(63));
    }

    @Test
    public void improvedPerformanceLargerSizeIncrease() {
        numOk += 200;
        max = 130;
        assertThat(throttlePolicy.calcNewMaxInFlight(dynamicFactor, numOk, prevOk, prevMax, max, queued), is(143));
        dynamicFactor = 100;
        assertThat(throttlePolicy.calcNewMaxInFlight(dynamicFactor, numOk, prevOk, prevMax, max, queued), is(156));
    }

    @Test
    public void improvedPerformanceLargerSizeButQueuedFalse() {
        numOk += 200;
        max = 130;
        queued = false;
        assertThat(throttlePolicy.calcNewMaxInFlight(dynamicFactor, numOk, prevOk, prevMax, max, queued), is(128));
    }

    @Test
    public void lowerPerformanceSameSizeShouldIncrease() {
        numOk -= 200;
        assertThat(throttlePolicy.calcNewMaxInFlight(dynamicFactor, numOk, prevOk, prevMax, max, queued), is(110));
    }

    @Test
    public void lowerPerformanceSmallerSizeShouldIncreaseSize() {
        numOk -= 200;
        max = 30;
        assertThat(throttlePolicy.calcNewMaxInFlight(dynamicFactor, numOk, prevOk, prevMax, max, queued), is(33));
        dynamicFactor = 100;
        assertThat(throttlePolicy.calcNewMaxInFlight(dynamicFactor, numOk, prevOk, prevMax, max, queued), is(36));
    }

    @Test
    public void lowerPerformanceLargerSizeTuneDownFurther() {
        numOk -= 200;
        max = 130;
        assertThat(throttlePolicy.calcNewMaxInFlight(dynamicFactor, numOk, prevOk, prevMax, max, queued), is(116));
    }
}
