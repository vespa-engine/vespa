// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.core;

import com.google.inject.Guice;
import com.yahoo.jdisc.Timer;
import org.junit.Test;

import static org.junit.Assert.assertTrue;


/**
 * @author Simon Thoresen Hult
 */
public class SystemTimerTestCase {

    @Test
    public void requireThatClassIsInjectedByDefault() {
        Timer timer = Guice.createInjector().getInstance(Timer.class);
        assertTrue(timer instanceof SystemTimer);
    }

    @Test
    public void requireThatSystemTimerIsSane() {
        long before = System.currentTimeMillis();
        long millis = new SystemTimer().currentTimeMillis();
        long after = System.currentTimeMillis();

        assertTrue(before <= millis);
        assertTrue(after >= millis);
    }
}
