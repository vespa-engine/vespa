// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.deployment;

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
import com.yahoo.vespa.hosted.controller.versions.NodeVersion;
import com.yahoo.vespa.hosted.controller.versions.NodeVersions;
import com.yahoo.vespa.hosted.controller.versions.VersionStatus;
import com.yahoo.vespa.hosted.controller.versions.VespaVersion;
import org.junit.Test;

import java.io.File;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

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
        Application failingInstance = tester.createApplication("domain1", "tenant1", "application1", "default");
        Application productionInstance = tester.createApplication("domain2", "tenant2", "application2", "default");
        Application instanceWithoutDeployment = tester.createApplication("domain3", "tenant3", "application3", "default");
        tester.deployCompletely(failingInstance, applicationPackage, 1L, false);
        tester.deployCompletely(productionInstance, applicationPackage, 2L, false);

        // Deploy once so that job information is stored, then remove the deployment
        tester.deployCompletely(instanceWithoutDeployment, applicationPackage, 3L, false);
        tester.controller().applications().deactivate(instanceWithoutDeployment.id().defaultInstance(), ZoneId.from("prod", "us-west-1"));

        // New version released
        version = Version.fromString("5.1");
        tester.containerTester().upgradeSystem(version);

        // Applications upgrade, 1/2 succeed
        tester.upgrader().maintain();
        tester.controller().applications().deploymentTrigger().triggerReadyJobs();
        tester.controller().applications().deploymentTrigger().triggerReadyJobs();
        tester.deployCompletely(failingInstance, applicationPackage, 1L, true);
        tester.deployCompletely(productionInstance, applicationPackage, 2L, false);

        tester.controller().updateVersionStatus(censorConfigServers(VersionStatus.compute(tester.controller()),
                                                                    tester.controller()));
        tester.assertResponse(authenticatedRequest("http://localhost:8080/deployment/v1/"), new File("root.json"));
    }

    private VersionStatus censorConfigServers(VersionStatus versionStatus, Controller controller) {
        List<VespaVersion> censored = new ArrayList<>();
        for (VespaVersion version : versionStatus.versions()) {
            if (version.nodeVersions().size() > 0) {
                version = new VespaVersion(version.statistics(),
                                           version.releaseCommit(),
                                           version.committedAt(),
                                           version.isControllerVersion(),
                                           version.isSystemVersion(),
                                           version.isReleased(),
                                           NodeVersions.EMPTY.with(new NodeVersion(HostName.from("config1.test"), version.versionNumber(), Instant.EPOCH))
                                                             .with(new NodeVersion(HostName.from("config2.test"), version.versionNumber(), Instant.EPOCH)),
                                           VespaVersion.confidenceFrom(version.statistics(), controller)
                );
            }
            censored.add(version);
        }
        return new VersionStatus(censored);
    }

}
