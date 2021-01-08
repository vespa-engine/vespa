// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.concurrent.maintenance;

import java.time.Duration;

/**
 * @author mpolden
 */
class TestMaintainer extends Maintainer {

    private int totalRuns = 0;
    private boolean success = true;
    private boolean throwing = false;

    public TestMaintainer(String name, JobControl jobControl, JobMetrics jobMetrics) {
        super(name, Duration.ofDays(1), Duration.ofDays(1), jobControl, jobMetrics);
    }

    public TestMaintainer(JobMetrics jobMetrics) {
        this(null, new JobControl(new JobControlStateMock()), jobMetrics);
    }

    public TestMaintainer(String name, JobControl jobControl) {
        this(name, jobControl, new JobMetrics((job, instant) -> {}));
    }

    public int totalRuns() {
        return totalRuns;
    }

    public TestMaintainer successOnNextRun(boolean success) {
        this.success = success;
        return this;
    }

    public TestMaintainer throwOnNextRun(boolean throwing) {
        this.throwing = throwing;
        return this;
    }

    @Override
    protected boolean maintain() {
        if (throwing) throw new RuntimeException("Maintenance run failed");
        totalRuns++;
        return success;
    }

}
