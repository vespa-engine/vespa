// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Environment;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.application.ApplicationPackage;
import com.yahoo.vespa.hosted.controller.deployment.ApplicationPackageBuilder;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentTester;
import org.junit.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author mpolden
 */
public class DatabaseMaintainerTest {

    @Test
    public void remove_pull_request_instances() {
        DeploymentTester tester = new DeploymentTester();
        DatabaseMaintainer maintainer = new DatabaseMaintainer(tester.controller(), Duration.ofDays(1), new JobControl(tester.controller().curator()));

        // Deploy regular application
        Application app1 = tester.createApplication("app1", "tenant1", 1, 1);
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .environment(Environment.prod)
                .region("us-east-3")
                .build();
        tester.deployCompletely(app1, applicationPackage);

        // Create pr instance (no longer permitted through ApplicationController so we have to cheat)
        Application app2 = new Application(ApplicationId.from("tenant1", "app1", "default-pr1"), Instant.now());
        tester.controller().curator().writeApplication(app2);
        assertTrue(tester.applications().get(app2.id()).isPresent());

        maintainer.maintain();
        assertTrue("Regular application is not deleted", tester.applications().get(app1.id()).isPresent());
        assertFalse("PR instance is deleted", tester.applications().get(app2.id()).isPresent());
    }

}
