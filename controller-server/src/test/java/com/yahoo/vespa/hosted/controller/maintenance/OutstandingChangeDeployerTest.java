// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Environment;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.SourceRevision;
import com.yahoo.vespa.hosted.controller.application.ApplicationPackage;
import com.yahoo.vespa.hosted.controller.application.Change;
import com.yahoo.vespa.hosted.controller.deployment.ApplicationPackageBuilder;
import com.yahoo.vespa.hosted.controller.deployment.InternalDeploymentTester;
import com.yahoo.vespa.hosted.controller.deployment.Run;
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
        InternalDeploymentTester tester = new InternalDeploymentTester();
        OutstandingChangeDeployer deployer = new OutstandingChangeDeployer(tester.controller(), Duration.ofMinutes(10),
                                                                           new JobControl(new MockCuratorDb()));
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .environment(Environment.prod)
                .region("us-west-1")
                .build();

        Application app1 = tester.createApplication("tenant", "app1", "default");
        tester.deployNewSubmission(app1.id(), tester.newSubmission(app1.id(), applicationPackage));

        Version version = new Version(6, 2);
        tester.deploymentTrigger().triggerChange(app1.id(), Change.of(version));
        tester.deploymentTrigger().triggerReadyJobs();

        assertEquals(Change.of(version), tester.application(app1.id()).change());
        assertFalse(tester.application(app1.id()).outstandingChange().hasTargets());

        assertEquals(1, tester.application(app1.id()).latestVersion().get().buildNumber().getAsLong());
        tester.newSubmission(app1.id(), applicationPackage, new SourceRevision("repository1", "master", "cafed00d"));

        ApplicationId instanceId = app1.id().defaultInstance();
        assertTrue(tester.application(app1.id()).outstandingChange().hasTargets());
        assertEquals("1.0.2-cafed00d", tester.application(app1.id()).outstandingChange().application().get().id());
        tester.assertRunning(instanceId, JobType.systemTest);
        tester.assertRunning(instanceId, JobType.stagingTest);
        assertEquals(2, tester.jobs().active().size());

        deployer.maintain();
        tester.deploymentTrigger().triggerReadyJobs();
        assertEquals("No effect as job is in progress", 2, tester.jobs().active().size());
        assertEquals("1.0.2-cafed00d", tester.application(app1.id()).outstandingChange().application().get().id());

        tester.runJob(instanceId, JobType.systemTest);
        tester.runJob(instanceId, JobType.stagingTest);
        tester.runJob(instanceId, JobType.productionUsWest1);
        tester.runJob(instanceId, JobType.systemTest);
        tester.runJob(instanceId, JobType.stagingTest);
        assertEquals("Upgrade done", 0, tester.jobs().active().size());

        deployer.maintain();
        tester.deploymentTrigger().triggerReadyJobs();
        assertEquals("1.0.2-cafed00d", tester.application(app1.id()).change().application().get().id());
        List<Run> runs = tester.jobs().active();
        assertEquals(1, runs.size());
        tester.assertRunning(instanceId, JobType.productionUsWest1);
        assertFalse(tester.application(app1.id()).outstandingChange().hasTargets());
    }

}
