// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Håkon Hallingstad
 */
class UnboundCodecFlagTest {
    @Test
    void testDuration() {
        verify("PT2H30M", Duration.ofHours(2).plus(Duration.ofMinutes(30)));
        verify("PT1.234S", Duration.ofMillis(1234));
    }

    private void verify(String serialized, Duration duration) {
        verify(serialized, duration, UnboundCodecFlag.Codec.DURATION);
    }

    /** This assumes equals() can be invoked on T. */
    private static <T> void verify(String serialized, T value, UnboundCodecFlag.Codec<T> codec) {
        assertEquals(serialized, codec.encode(value));
        assertEquals(value, codec.decode(serialized));
    }
}