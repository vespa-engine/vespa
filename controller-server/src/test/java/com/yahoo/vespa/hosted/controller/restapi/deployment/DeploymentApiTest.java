// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.deployment;

import com.yahoo.component.Version;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.ControllerTester;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType;
import com.yahoo.vespa.hosted.controller.application.ApplicationPackage;
import com.yahoo.vespa.hosted.controller.deployment.ApplicationPackageBuilder;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentTester;
import com.yahoo.vespa.hosted.controller.restapi.ContainerTester;
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
        ContainerTester tester = new ContainerTester(container, responseFiles);
        DeploymentTester deploymentTester = new DeploymentTester(new ControllerTester(tester));
        Version version = Version.fromString("5.0");
        deploymentTester.controllerTester().upgradeSystem(version);
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .environment(Environment.prod)
                .region("us-west-1")
                .build();

        // 3 applications deploy on current system version
        var failingApp = deploymentTester.newDeploymentContext("tenant1", "application1", "default");
        var productionApp = deploymentTester.newDeploymentContext("tenant2", "application2", "default");
        var appWithoutDeployments = deploymentTester.newDeploymentContext("tenant3", "application3", "default");
        failingApp.submit(applicationPackage).deploy();
        productionApp.submit(applicationPackage).deploy();

        // Deploy once so that job information is stored, then remove the deployment
        appWithoutDeployments.submit(applicationPackage).deploy();
        deploymentTester.applications().deactivate(appWithoutDeployments.instanceId(), ZoneId.from("prod", "us-west-1"));

        // New version released
        version = Version.fromString("5.1");
        deploymentTester.controllerTester().upgradeSystem(version);

        // Applications upgrade, 1/2 succeed
        deploymentTester.upgrader().maintain();
        deploymentTester.triggerJobs();
        productionApp.deployPlatform(version);
        failingApp.runJob(JobType.systemTest).failDeployment(JobType.stagingTest);
        deploymentTester.triggerJobs();

        tester.controller().updateVersionStatus(censorConfigServers(VersionStatus.compute(tester.controller())));
        tester.assertResponse(authenticatedRequest("http://localhost:8080/deployment/v1/"), new File("root.json"));
    }

    private VersionStatus censorConfigServers(VersionStatus versionStatus) {
        List<VespaVersion> censored = new ArrayList<>();
        for (VespaVersion version : versionStatus.versions()) {
            if (version.nodeVersions().size() > 0) {
                version = new VespaVersion(version.statistics(),
                                           version.releaseCommit(),
                                           version.committedAt(),
                                           version.isControllerVersion(),
                                           version.isSystemVersion(),
                                           version.isReleased(),
                                           NodeVersions.EMPTY.with(List.of(new NodeVersion(HostName.from("config1.test"), ZoneId.defaultId(), version.versionNumber(), version.versionNumber(), Instant.EPOCH),
                                                                           new NodeVersion(HostName.from("config2.test"), ZoneId.defaultId(), version.versionNumber(), version.versionNumber(), Instant.EPOCH))),
                                           version.confidence()
                );
            }
            censored.add(version);
        }
        return new VersionStatus(censored);
    }

}
