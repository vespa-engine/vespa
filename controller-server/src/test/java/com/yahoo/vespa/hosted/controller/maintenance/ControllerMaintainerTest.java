// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.config.provision.SystemName;
import com.yahoo.vespa.hosted.controller.ControllerTester;
import com.yahoo.vespa.hosted.controller.integration.MetricsMock;
import org.junit.Before;
import org.junit.Test;

import java.time.Duration;
import java.util.EnumSet;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;

/**
 * @author mpolden
 */
public class ControllerMaintainerTest {

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
    public void records_metric() {
        maintainerIn(SystemName.main, new AtomicInteger()).run();
        MetricsMock metrics = (MetricsMock) tester.controller().metric();
        assertEquals(0L, metrics.getMetric((context) -> "MockMaintainer".equals(context.get("job")),
                                           "maintenance.secondsSinceSuccess").get());
    }

    private ControllerMaintainer maintainerIn(SystemName system, AtomicInteger executions) {
        return new ControllerMaintainer(tester.controller(), Duration.ofDays(1),
                                        "MockMaintainer", EnumSet.of(system)) {
            @Override
            protected boolean maintain() {
                executions.incrementAndGet();
                return true;
            }
        };
    }

}
