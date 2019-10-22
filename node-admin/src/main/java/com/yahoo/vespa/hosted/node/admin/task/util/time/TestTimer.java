// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.time;

import com.yahoo.jdisc.Timer;

import java.time.Duration;

/**
 * @author hakonhall
 */
public class TestTimer implements Timer {
    private Duration durationSinceEpoch = Duration.ZERO;

    public void setMillis(long millisSinceEpoch) {
        durationSinceEpoch = Duration.ofMillis(millisSinceEpoch);
    }

    public void advanceMillis(long millis) { advance(Duration.ofMillis(millis)); }
    public void advanceSeconds(long seconds) { advance(Duration.ofSeconds(seconds)); }
    public void advanceMinutes(long minutes) { advance(Duration.ofMinutes(minutes)); }
    public void advance(Duration duration) {
        durationSinceEpoch = durationSinceEpoch.plus(duration);
    }

    @Override
    public long currentTimeMillis() {
        return durationSinceEpoch.toMillis();
    }
}
