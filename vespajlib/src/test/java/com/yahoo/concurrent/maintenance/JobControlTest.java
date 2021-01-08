// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.concurrent.maintenance;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author bratseth
 */
public class JobControlTest {

    @Test
    public void testJobControl() {
        JobControlStateMock state = new JobControlStateMock();
        JobControl jobControl = new JobControl(state);

        String job1 = "Job1";
        String job2 = "Job2";
        JobMetrics metrics = new JobMetrics((job, instant) -> {});
        TestMaintainer maintainer1 = new TestMaintainer(job1, jobControl, metrics);
        TestMaintainer maintainer2 = new TestMaintainer(job2, jobControl, metrics);
        assertEquals(2, jobControl.jobs().size());
        assertTrue(jobControl.jobs().contains(job1));
        assertTrue(jobControl.jobs().contains(job2));

        assertTrue(jobControl.isActive(job1));
        assertTrue(jobControl.isActive(job2));

        state.setActive(job1, false);
        assertFalse(jobControl.isActive(job1));
        assertTrue(jobControl.isActive(job2));

        state.setActive(job2, false);
        assertFalse(jobControl.isActive(job1));
        assertFalse(jobControl.isActive(job2));

        state.setActive(job1, true);
        assertTrue(jobControl.isActive(job1));
        assertFalse(jobControl.isActive(job2));

        state.setActive(job2, true);
        assertTrue(jobControl.isActive(job1));
        assertTrue(jobControl.isActive(job2));

        // Run jobs on-demand
        jobControl.run(job1);
        jobControl.run(job1);
        assertEquals(2, maintainer1.totalRuns());
        jobControl.run(job2);
        assertEquals(1, maintainer2.totalRuns());

        // Running jobs on-demand ignores inactive flag
        state.setActive(job1, false);
        jobControl.run(job1);
        assertEquals(3, maintainer1.totalRuns());
    }

    @Test
    public void testJobControlMayDeactivateJobs() {
        JobControlStateMock state = new JobControlStateMock();
        JobControl jobControl = new JobControl(state);
        TestMaintainer mockMaintainer = new TestMaintainer(null, jobControl, new JobMetrics((job, instant) -> {}));

        assertTrue(jobControl.jobs().contains("TestMaintainer"));

        assertEquals(0, mockMaintainer.totalRuns());

        mockMaintainer.run();
        assertEquals(1, mockMaintainer.totalRuns());

        state.setActive("TestMaintainer", false);
        mockMaintainer.run();
        assertEquals(1, mockMaintainer.totalRuns());

        state.setActive("TestMaintainer", true);
        mockMaintainer.run();
        assertEquals(2, mockMaintainer.totalRuns());
    }

}
