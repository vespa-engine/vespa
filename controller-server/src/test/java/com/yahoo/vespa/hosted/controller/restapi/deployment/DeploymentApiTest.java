// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.deployment;

import com.google.common.collect.ImmutableSet;
import com.yahoo.component.Version;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.application.ApplicationPackage;
import com.yahoo.vespa.hosted.controller.deployment.ApplicationPackageBuilder;
import com.yahoo.vespa.hosted.controller.restapi.ContainerControllerTester;
import com.yahoo.vespa.hosted.controller.restapi.ControllerContainerTest;
import com.yahoo.vespa.hosted.controller.versions.VersionStatus;
import com.yahoo.vespa.hosted.controller.versions.VespaVersion;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author bratseth
 */
public class DeploymentApiTest extends ControllerContainerTest {

    private final static String responseFiles = "src/test/java/com/yahoo/vespa/hosted/controller/restapi/deployment/responses/";

    @Test
    public void testDeploymentApi() {
        ContainerControllerTester tester = new ContainerControllerTester(container, responseFiles);
        Version version = Version.fromString("5.0");
        tester.containerTester().upgradeSystem(version);
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .environment(Environment.prod)
                .region("us-west-1")
                .build();

        // 3 applications deploy on current system version
        Application failingApplication = tester.createApplication("domain1", "tenant1",
                                                                  "application1");
        Application productionApplication = tester.createApplication("domain2", "tenant2",
                                                                     "application2");
        Application applicationWithoutDeployment = tester.createApplication("domain3", "tenant3",
                                                                             "application3");
        tester.deployCompletely(failingApplication, applicationPackage, 1L, false);
        tester.deployCompletely(productionApplication, applicationPackage, 2L, false);

        // Deploy once so that job information is stored, then remove the deployment
        tester.deployCompletely(applicationWithoutDeployment, applicationPackage, 3L, false);
        tester.controller().applications().deactivate(applicationWithoutDeployment.id(), ZoneId.from("prod", "us-west-1"));

        // New version released
        version = Version.fromString("5.1");
        tester.containerTester().upgradeSystem(version);

        // Applications upgrade, 1/2 succeed
        tester.upgrader().maintain();
        tester.controller().applications().deploymentTrigger().triggerReadyJobs();
        tester.controller().applications().deploymentTrigger().triggerReadyJobs();
        tester.deployCompletely(failingApplication, applicationPackage, 1L, true);
        tester.deployCompletely(productionApplication, applicationPackage, 2L, false);

        tester.controller().updateVersionStatus(censorConfigServers(VersionStatus.compute(tester.controller()),
                                                                    tester.controller()));
        tester.assertResponse(authenticatedRequest("http://localhost:8080/deployment/v1/"), new File("root.json"));
    }

    private VersionStatus censorConfigServers(VersionStatus versionStatus, Controller controller) {
        List<VespaVersion> censored = new ArrayList<>();
        for (VespaVersion version : versionStatus.versions()) {
            if (!version.systemApplicationHostnames().isEmpty()) {
                version = new VespaVersion(version.statistics(),
                                           version.releaseCommit(),
                                           version.committedAt(),
                                           version.isControllerVersion(),
                                           version.isSystemVersion(),
                                           ImmutableSet.of("config1.test", "config2.test").stream()
                                                       .map(HostName::from)
                                                       .collect(Collectors.toSet()),
                                           VespaVersion.confidenceFrom(version.statistics(), controller)
                );
            }
            censored.add(version);
        }
        return new VersionStatus(censored);
    }

}
