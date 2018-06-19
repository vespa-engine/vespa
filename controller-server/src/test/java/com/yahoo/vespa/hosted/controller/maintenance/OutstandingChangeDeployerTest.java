// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.component.Version;
import com.yahoo.config.provision.Environment;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.api.integration.BuildService;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType;
import com.yahoo.vespa.hosted.controller.application.ApplicationPackage;
import com.yahoo.vespa.hosted.controller.application.Change;
import com.yahoo.vespa.hosted.controller.application.SourceRevision;
import com.yahoo.vespa.hosted.controller.deployment.ApplicationPackageBuilder;
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
        OutstandingChangeDeployer deployer = new OutstandingChangeDeployer(tester.controller(), Duration.ofMinutes(10),
                                                                           new JobControl(new MockCuratorDb()));
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .environment(Environment.prod)
                .region("us-west-1")
                .build();

        tester.createAndDeploy("app1", 11, applicationPackage);
        tester.createAndDeploy("app2", 22, applicationPackage);

        Version version = new Version(6, 2);
        tester.deploymentTrigger().triggerChange(tester.application("app1").id(), Change.of(version));
        tester.deploymentTrigger().triggerReadyJobs();

        assertEquals(Change.of(version), tester.application("app1").change());
        assertFalse(tester.application("app1").outstandingChange().isPresent());

        tester.jobCompletion(JobType.component)
              .application(tester.application("app1"))
              .sourceRevision(new SourceRevision("repository1","master", "cafed00d"))
              .nextBuildNumber()
              .uploadArtifact(applicationPackage)
              .submit();

        Application app = tester.application("app1");
        assertTrue(app.outstandingChange().isPresent());
        assertEquals("1.0.43-cafed00d", app.outstandingChange().application().get().id());
        assertEquals(2, tester.buildService().jobs().size());

        deployer.maintain();
        tester.deploymentTrigger().triggerReadyJobs();
        assertEquals("No effect as job is in progress", 2, tester.buildService().jobs().size());
        assertEquals("1.0.43-cafed00d", app.outstandingChange().application().get().id());

        tester.deployAndNotify(app, applicationPackage, true, JobType.systemTest);
        tester.deployAndNotify(app, applicationPackage, true, JobType.stagingTest);
        tester.deployAndNotify(app, applicationPackage, true, JobType.productionUsWest1);
        assertEquals("Upgrade done", 0, tester.buildService().jobs().size());

        deployer.maintain();
        tester.deploymentTrigger().triggerReadyJobs();
        app = tester.application("app1");
        assertEquals("1.0.43-cafed00d", app.change().application().get().id());
        List<BuildService.BuildJob> jobs = tester.buildService().jobs();
        assertEquals(2, jobs.size());
        assertEquals(11, jobs.get(0).projectId());
        tester.assertRunning(JobType.systemTest, app.id());
        tester.assertRunning(JobType.stagingTest, app.id());
        assertFalse(tester.application("app1").outstandingChange().isPresent());
    }

}
