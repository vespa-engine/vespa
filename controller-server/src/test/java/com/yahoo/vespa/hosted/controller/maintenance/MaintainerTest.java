// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.vespa.hosted.controller.ControllerTester;
import org.junit.Before;
import org.junit.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;

/**
 * @author mpolden
 */
public class MaintainerTest {

    private ControllerTester tester;

    @Before
    public void before() {
        tester = new ControllerTester();
    }

    @Test
    public void only_runs_in_permitted_systems() {
        AtomicInteger executions = new AtomicInteger();
        maintainerIn(SystemName.cd, executions).run();
        maintainerIn(SystemName.main, executions).run();
        assertEquals(1, executions.get());
    }

    @Test
    public void staggering() {
        List<HostName> cluster = List.of(HostName.from("cfg1"), HostName.from("cfg2"), HostName.from("cfg3"));
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

    private Maintainer maintainerIn(SystemName system, AtomicInteger executions) {
        return new Maintainer(tester.controller(), Duration.ofDays(1), new JobControl(tester.controller().curator()),
                              "MockMaintainer", EnumSet.of(system)) {
            @Override
            protected void maintain() {
                executions.incrementAndGet();
            }
        };
    }

}
