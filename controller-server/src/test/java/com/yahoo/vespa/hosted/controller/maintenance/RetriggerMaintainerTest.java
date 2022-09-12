// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.application.pkg.ApplicationPackage;
import com.yahoo.vespa.hosted.controller.deployment.ApplicationPackageBuilder;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentContext;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentTester;
import com.yahoo.vespa.hosted.controller.deployment.RetriggerEntry;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author mortent
 */
public class RetriggerMaintainerTest {

    private final DeploymentTester tester = new DeploymentTester();

    @Test
    void processes_queue() {
        RetriggerMaintainer maintainer = new RetriggerMaintainer(tester.controller(), Duration.ofDays(1));
        ApplicationId applicationId = ApplicationId.from("tenant", "app", "default");
        var devApp = tester.newDeploymentContext(applicationId);
        ApplicationPackage appPackage = new ApplicationPackageBuilder()
                .region("us-west-1")
                .build();

        // Deploy app
        devApp.runJob(DeploymentContext.devUsEast1, appPackage);
        devApp.completeRollout();

        // Trigger a run (to simulate a running job)
        tester.deploymentTrigger().reTrigger(applicationId, DeploymentContext.devUsEast1, null);

        // Add a job to the queue
        tester.deploymentTrigger().reTriggerOrAddToQueue(devApp.deploymentIdIn(ZoneId.from("dev", "us-east-1")), null);

        // Should be 1 entry in the queue:
        List<RetriggerEntry> retriggerEntries = tester.controller().curator().readRetriggerEntries();
        assertEquals(1, retriggerEntries.size());

        // Adding to queue triggers abort
        devApp.jobAborted(DeploymentContext.devUsEast1);
        assertEquals(0, tester.jobs().active(applicationId).size());

        // The maintainer runs and will actually trigger dev us-east, but keeps the entry in queue to verify it was actually run
        maintainer.maintain();
        retriggerEntries = tester.controller().curator().readRetriggerEntries();
        assertEquals(1, retriggerEntries.size());
        assertEquals(1, tester.jobs().active(applicationId).size());

        // Run outstanding jobs
        devApp.runJob(DeploymentContext.devUsEast1);
        assertEquals(0, tester.jobs().active(applicationId).size());

        // Run maintainer again, should find that the job has already run successfully and will remove the entry.
        maintainer.maintain();
        retriggerEntries = tester.controller().curator().readRetriggerEntries();
        assertEquals(0, retriggerEntries.size());
        assertEquals(0, tester.jobs().active(applicationId).size());
    }
}
