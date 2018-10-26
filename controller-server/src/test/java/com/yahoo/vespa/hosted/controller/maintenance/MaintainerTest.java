// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.config.provision.SystemName;
import com.yahoo.vespa.hosted.controller.ControllerTester;
import org.junit.Before;
import org.junit.Test;

import java.time.Duration;
import java.util.EnumSet;
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
