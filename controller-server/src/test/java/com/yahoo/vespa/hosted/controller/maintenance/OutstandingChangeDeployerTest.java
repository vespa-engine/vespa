// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.component.Version;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.SourceRevision;
import com.yahoo.vespa.hosted.controller.application.ApplicationPackage;
import com.yahoo.vespa.hosted.controller.application.Change;
import com.yahoo.vespa.hosted.controller.deployment.ApplicationPackageBuilder;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentTester;
import com.yahoo.vespa.hosted.controller.deployment.Run;
import org.junit.Test;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

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
        OutstandingChangeDeployer deployer = new OutstandingChangeDeployer(tester.controller(), Duration.ofMinutes(10));
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .region("us-west-1")
                .build();

        var app1 = tester.newDeploymentContext("tenant", "app1", "default").submit(applicationPackage).deploy();

        Version version = new Version(6, 2);
        tester.deploymentTrigger().triggerChange(app1.instanceId(), Change.of(version));
        tester.deploymentTrigger().triggerReadyJobs();

        assertEquals(Change.of(version), app1.instance().change());
        assertFalse(app1.deploymentStatus().outstandingChange(app1.instance().name()).hasTargets());

        assertEquals(1, app1.application().latestVersion().get().buildNumber().getAsLong());
        app1.submit(applicationPackage, Optional.of(new SourceRevision("repository1", "master", "cafed00d")));

        assertTrue(app1.deploymentStatus().outstandingChange(app1.instance().name()).hasTargets());
        assertEquals("1.0.2-cafed00d", app1.deploymentStatus().outstandingChange(app1.instance().name()).application().get().id());
        app1.assertRunning(JobType.systemTest);
        app1.assertRunning(JobType.stagingTest);
        assertEquals(2, tester.jobs().active().size());

        deployer.maintain();
        tester.triggerJobs();
        assertEquals("No effect as job is in progress", 2, tester.jobs().active().size());
        assertEquals("1.0.2-cafed00d", app1.deploymentStatus().outstandingChange(app1.instance().name()).application().get().id());

        app1.runJob(JobType.systemTest).runJob(JobType.stagingTest).runJob(JobType.productionUsWest1)
            .runJob(JobType.stagingTest).runJob(JobType.systemTest);
        assertEquals("Upgrade done", 0, tester.jobs().active().size());

        deployer.maintain();
        tester.triggerJobs();
        assertEquals("1.0.2-cafed00d", app1.instance().change().application().get().id());
        List<Run> runs = tester.jobs().active();
        assertEquals(1, runs.size());
        app1.assertRunning(JobType.productionUsWest1);
        assertFalse(app1.deploymentStatus().outstandingChange(app1.instance().name()).hasTargets());
    }

}
