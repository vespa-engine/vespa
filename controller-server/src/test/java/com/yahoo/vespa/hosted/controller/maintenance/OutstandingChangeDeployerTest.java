// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.component.Version;
import com.yahoo.vespa.hosted.controller.api.integration.BuildService;
import com.yahoo.vespa.hosted.controller.application.Change;
import com.yahoo.vespa.hosted.controller.application.DeploymentJobs;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentTester;
import com.yahoo.vespa.hosted.controller.persistence.MockCuratorDb;
import org.junit.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author bratseth
 */
public class OutstandingChangeDeployerTest {

    @Test
    public void testChangeDeployer() {
        DeploymentTester tester = new DeploymentTester();
        tester.configServer().setDefaultVersion(new Version(6, 1));
        OutstandingChangeDeployer deployer = new OutstandingChangeDeployer(tester.controller(), Duration.ofMinutes(10),
                                                                           new JobControl(new MockCuratorDb()));
        tester.createAndDeploy("app1", 11, "default");
        tester.createAndDeploy("app2", 22, "default");

        Version version = new Version(6, 2);
        tester.deploymentTrigger().triggerChange(tester.application("app1").id(), new Change.VersionChange(version));

        assertEquals(new Change.VersionChange(version), tester.application("app1").deploying());
        assertFalse(tester.application("app1").hasOutstandingChange());
        tester.notifyJobCompletion(DeploymentJobs.JobType.component, tester.application("app1"), true);
        assertTrue(tester.application("app1").hasOutstandingChange());
        assertEquals(1, tester.buildSystem().jobs().size());

        deployer.maintain();
        assertEquals("No effect as job is in progress", 1, tester.buildSystem().jobs().size());

        tester.deployCompletely("app1");
        assertEquals("Upgrade done", 0, tester.buildSystem().jobs().size());

        deployer.maintain();
        List<BuildService.BuildJob> jobs = tester.buildSystem().jobs();
        assertEquals(1, jobs.size());
        assertEquals(11, jobs.get(0).projectId());
        assertEquals(DeploymentJobs.JobType.systemTest.jobName(), jobs.get(0).jobName());
        assertFalse(tester.application("app1").hasOutstandingChange());
    }

}
