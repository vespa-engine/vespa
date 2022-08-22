// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.component.Version;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.ApplicationVersion;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.RevisionId;
import com.yahoo.vespa.hosted.controller.application.Change;
import com.yahoo.vespa.hosted.controller.application.pkg.ApplicationPackage;
import com.yahoo.vespa.hosted.controller.deployment.ApplicationPackageBuilder;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentTester;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author bratseth
 */
public class OutstandingChangeDeployerTest {

    @Test
    void testChangeDeployer() {
        DeploymentTester tester = new DeploymentTester();
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .region("us-west-1")
                .revisionChange("when-failing")
                .build();

        var app = tester.newDeploymentContext().submit(applicationPackage).deploy();

        Version version = new Version(6, 2);
        tester.deploymentTrigger().forceChange(app.instanceId(), Change.of(version));
        assertEquals(Change.of(version), app.instance().change());
        assertFalse(app.deploymentStatus().outstandingChange(app.instance().name()).hasTargets());

        app.submit(applicationPackage);
        Optional<RevisionId> revision = app.lastSubmission();
        assertFalse(app.deploymentStatus().outstandingChange(app.instance().name()).hasTargets());
        assertEquals(Change.of(version).with(revision.get()), app.instance().change());

        app.submit(applicationPackage);
        Optional<RevisionId> outstanding = app.lastSubmission();
        assertTrue(app.deploymentStatus().outstandingChange(app.instance().name()).hasTargets());
        assertEquals(Change.of(version).with(revision.get()), app.instance().change());

        tester.outstandingChangeDeployer().run();
        assertTrue(app.deploymentStatus().outstandingChange(app.instance().name()).hasTargets());
        assertEquals(Change.of(version).with(revision.get()), app.instance().change());

        app.deploy();
        tester.outstandingChangeDeployer().run();
        assertFalse(app.deploymentStatus().outstandingChange(app.instance().name()).hasTargets());
        assertEquals(Change.of(outstanding.get()), app.instance().change());
    }

}
