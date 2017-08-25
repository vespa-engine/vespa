// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.ControllerTester;
import com.yahoo.vespa.hosted.controller.persistence.MockCuratorDb;
import org.junit.Test;

import java.time.Duration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author bratseth
 */
public class JobControlTest {
    
    @Test
    public void testJobControl() {
        JobControl jobControl = new JobControl(new MockCuratorDb());

        assertTrue(jobControl.jobs().isEmpty());

        String job1 = "Job1";
        String job2 = "Job2";

        jobControl.started(job1);
        jobControl.started(job2);
        assertEquals(2, jobControl.jobs().size());
        assertTrue(jobControl.jobs().contains(job1));
        assertTrue(jobControl.jobs().contains(job2));

        assertTrue(jobControl.isActive(job1));
        assertTrue(jobControl.isActive(job2));

        jobControl.setActive(job1, false);
        assertFalse(jobControl.isActive(job1));
        assertTrue(jobControl.isActive(job2));

        jobControl.setActive(job2, false);
        assertFalse(jobControl.isActive(job1));
        assertFalse(jobControl.isActive(job2));

        jobControl.setActive(job1, true);
        assertTrue(jobControl.isActive(job1));
        assertFalse(jobControl.isActive(job2));

        jobControl.setActive(job2, true);
        assertTrue(jobControl.isActive(job1));
        assertTrue(jobControl.isActive(job2));
    }
    
    @Test
    public void testJobControlMayDeactivateJobs() {
        JobControl jobControl = new JobControl(new MockCuratorDb());

        ControllerTester tester = new ControllerTester();
        MockMaintainer mockMaintainer = new MockMaintainer(tester.controller(), jobControl);

        assertTrue(jobControl.jobs().contains("MockMaintainer"));

        assertEquals(0, mockMaintainer.maintenanceInvocations);

        mockMaintainer.run();
        assertEquals(1, mockMaintainer.maintenanceInvocations);

        jobControl.setActive("MockMaintainer", false);
        mockMaintainer.run();
        assertEquals(1, mockMaintainer.maintenanceInvocations);

        jobControl.setActive("MockMaintainer", true);
        mockMaintainer.run();
        assertEquals(2, mockMaintainer.maintenanceInvocations);
    }
    
    private static class MockMaintainer extends Maintainer {
        
        int maintenanceInvocations = 0;
        
        public MockMaintainer(Controller controller, JobControl jobControl) {
            super(controller, Duration.ofHours(1), jobControl);
        }

        @Override
        protected void maintain() {
            maintenanceInvocations++;
        }

    }
    
}
