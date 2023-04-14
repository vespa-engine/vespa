// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.config.provision.SystemName;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.ControllerTester;
import com.yahoo.vespa.hosted.controller.integration.MetricsMock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.EnumSet;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author mpolden
 */
public class ControllerMaintainerTest {

    private ControllerTester tester;

    @BeforeEach
    public void before() {
        tester = new ControllerTester();
    }

    @Test
    void only_runs_in_permitted_systems() {
        AtomicInteger executions = new AtomicInteger();
        new TestControllerMaintainer(tester.controller(), SystemName.cd, executions).run();
        new TestControllerMaintainer(tester.controller(), SystemName.main, executions).run();
        assertEquals(1, executions.get());
    }

    @Test
    void records_metric() {
        TestControllerMaintainer maintainer = new TestControllerMaintainer(tester.controller(), SystemName.main, new AtomicInteger());
        maintainer.run();
        assertEquals(0.0, successFactorDeviationMetric(), 0.0000001);
        maintainer.success = false;
        maintainer.run();
        maintainer.run();
        assertEquals(1.0, successFactorDeviationMetric(), 0.0000001);
        maintainer.success = true;
        maintainer.run();
        assertEquals(0.0, successFactorDeviationMetric(), 0.0000001);
    }

    private long consecutiveFailuresMetric() {
        MetricsMock metrics = (MetricsMock) tester.controller().metric();
        return metrics.getMetric((context) -> "TestControllerMaintainer".equals(context.get("job")),
                                 "maintenance.consecutiveFailures").get().longValue();
    }

    private long successFactorDeviationMetric() {
        MetricsMock metrics = (MetricsMock) tester.controller().metric();
        return metrics.getMetric((context) -> "TestControllerMaintainer".equals(context.get("job")),
                                 "maintenance.successFactorDeviation").get().longValue();
    }

    private static class TestControllerMaintainer extends ControllerMaintainer {

        private final AtomicInteger executions;
        private boolean success = true;

        public TestControllerMaintainer(Controller controller, SystemName system, AtomicInteger executions) {
            super(controller, Duration.ofDays(1), null, EnumSet.of(system));
            this.executions = executions;
        }

        @Override
        protected double maintain() {
            executions.incrementAndGet();
            return success ? 0.0 : 1.0;
        }

    }

}
