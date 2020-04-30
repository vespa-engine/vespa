// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.concurrent.maintenance;

import org.junit.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * @author freva
 */
public class MaintainerTest {

    @Test
    public void staggering() {
        List<String> cluster = List.of("cfg1", "cfg2", "cfg3");
        Duration interval = Duration.ofMillis(300);
        Instant now = Instant.ofEpochMilli(1000);
        assertEquals(200, Maintainer.staggeredDelay(interval, now, "cfg1", cluster).toMillis());
        assertEquals(0, Maintainer.staggeredDelay(interval, now, "cfg2", cluster).toMillis());
        assertEquals(100, Maintainer.staggeredDelay(interval, now, "cfg3", cluster).toMillis());

        now = Instant.ofEpochMilli(1001);
        assertEquals(199, Maintainer.staggeredDelay(interval, now, "cfg1", cluster).toMillis());
        assertEquals(299, Maintainer.staggeredDelay(interval, now, "cfg2", cluster).toMillis());
        assertEquals(99, Maintainer.staggeredDelay(interval, now, "cfg3", cluster).toMillis());

        now = Instant.ofEpochMilli(1101);
        assertEquals(99, Maintainer.staggeredDelay(interval, now, "cfg1", cluster).toMillis());
        assertEquals(199, Maintainer.staggeredDelay(interval, now, "cfg2", cluster).toMillis());
        assertEquals(299, Maintainer.staggeredDelay(interval, now, "cfg3", cluster).toMillis());

        assertEquals(300, Maintainer.staggeredDelay(interval, now, "cfg0", cluster).toMillis());
    }

}
