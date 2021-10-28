// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.streamingvisitors.tracing;

import java.util.Random;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class MockUtils {

    public static MonotonicNanoClock mockedClockReturning(Long ts, Long... additionalTimestamps) {
        var clock = mock(MonotonicNanoClock.class);
        when(clock.nanoTimeNow()).thenReturn(ts, additionalTimestamps);
        return clock;
    }

    // Extremely high quality randomness :D
    public static Random mockedRandomReturning(Double v, Double... additionalValues) {
        var rng = mock(Random.class);
        when(rng.nextDouble()).thenReturn(v, additionalValues);
        return rng;
    }

}
