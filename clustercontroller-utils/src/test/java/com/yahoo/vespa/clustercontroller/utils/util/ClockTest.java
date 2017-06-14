// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.utils.util;

import junit.framework.TestCase;

public class ClockTest extends TestCase {

    public void testNothingButGetCoverage() {
        long s = new Clock().getTimeInSecs();
        long ms = new Clock().getTimeInMillis();
        assertTrue(ms >= 1000 * s);
    }
}
