// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.NodeRepositoryTester;
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
        NodeRepositoryTester tester = new NodeRepositoryTester();
        JobControl jobControl = new JobControl(tester.nodeRepository().database());
        
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
        NodeRepositoryTester tester = new NodeRepositoryTester();
        JobControl jobControl = tester.nodeRepository().jobControl();
        MockMaintainer mockMaintainer = new MockMaintainer(tester.nodeRepository());
        
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
        
        private MockMaintainer(NodeRepository nodeRepository) {
            super(nodeRepository, Duration.ofHours(1));
        }

        @Override
        protected void maintain() {
            maintenanceInvocations++;
        }

    }
    
}
