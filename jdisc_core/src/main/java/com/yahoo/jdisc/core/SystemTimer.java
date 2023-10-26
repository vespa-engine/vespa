// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.core;

import com.yahoo.jdisc.Timer;

import java.time.Instant;

/**
 * A timer which returns the System time
 *
 * @author Simon Thoresen Hult
 */
public class SystemTimer implements Timer {

    @Override
    public long currentTimeMillis() {
        return System.currentTimeMillis();
    }

    @Override
    public Instant currentTime() {
        return Instant.now();
    }
}
