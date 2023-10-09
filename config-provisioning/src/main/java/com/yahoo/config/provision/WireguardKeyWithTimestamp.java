// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import com.yahoo.jdisc.Timer;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Random;

/**
 * @author gjoranv
 */
public record WireguardKeyWithTimestamp(WireguardKey key, Instant timestamp) {

    public static final int KEY_ROTATION_BASE = 60;
    public static final int KEY_ROTATION_VARIANCE = 10;
    public static final int KEY_EXPIRY = KEY_ROTATION_BASE + KEY_ROTATION_VARIANCE + 5;

    public WireguardKeyWithTimestamp {
        if (key == null) throw new IllegalArgumentException("Wireguard key cannot be null");
        if (timestamp == null) timestamp = Instant.EPOCH;
    }

    public static WireguardKeyWithTimestamp from(String key, long msTimestamp) {
        return new WireguardKeyWithTimestamp(WireguardKey.from(key), Instant.ofEpochMilli(msTimestamp));
    }

    public boolean isDueForRotation(Timer timer, ChronoUnit unit, Random random) {
        return timer.currentTime().isAfter(keyRotationDueAt(unit, random));
    }

    public boolean hasExpired(Timer timer, ChronoUnit unit) {
        return timer.currentTime().isAfter(timestamp.plus(KEY_EXPIRY, unit));
    }

    private Instant keyRotationDueAt(ChronoUnit unit, Random random) {
        return timestamp.plus(KEY_ROTATION_BASE + random.nextInt(KEY_ROTATION_VARIANCE), unit);
    }

}
