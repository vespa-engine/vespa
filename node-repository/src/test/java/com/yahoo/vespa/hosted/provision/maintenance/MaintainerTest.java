// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.config.provision.HostName;
import org.junit.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * @author freva
 */
public class MaintainerTest {

    @Test
    public void staggering() {
        List<HostName> cluster = Arrays.asList(HostName.from("cfg1"), HostName.from("cfg2"), HostName.from("cfg3"));
        Instant now = Instant.ofEpochMilli(1001);
        Duration interval = Duration.ofMillis(300);
        assertEquals(299, Maintainer.staggeredDelay(cluster, HostName.from("cfg1"), now, interval));
        assertEquals(399, Maintainer.staggeredDelay(cluster, HostName.from("cfg2"), now, interval));
        assertEquals(199, Maintainer.staggeredDelay(cluster, HostName.from("cfg3"), now, interval));
        now = Instant.ofEpochMilli(1101);
        assertEquals(199, Maintainer.staggeredDelay(cluster, HostName.from("cfg1"), now, interval));
        assertEquals(299, Maintainer.staggeredDelay(cluster, HostName.from("cfg2"), now, interval));
        assertEquals(399, Maintainer.staggeredDelay(cluster, HostName.from("cfg3"), now, interval));
        assertEquals(300, Maintainer.staggeredDelay(cluster, HostName.from("cfg0"), now, interval));
    }
}
