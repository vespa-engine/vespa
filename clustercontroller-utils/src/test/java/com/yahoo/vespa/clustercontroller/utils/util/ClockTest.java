// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.utils.util;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class ClockTest {

    @Test
    public void testNothingButGetCoverage() {
        long s = new Clock().getTimeInSecs();
        long ms = new Clock().getTimeInMillis();
        assertTrue(ms >= 1000 * s);
    }

}
