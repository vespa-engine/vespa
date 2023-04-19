// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.deployment;

import com.yahoo.component.Version;
import com.yahoo.config.application.api.ValidationId;
import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.ControllerTester;
import com.yahoo.vespa.hosted.controller.application.pkg.ApplicationPackage;
import com.yahoo.vespa.hosted.controller.deployment.ApplicationPackageBuilder;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentContext;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentTester;
import com.yahoo.vespa.hosted.controller.restapi.ContainerTester;
import com.yahoo.vespa.hosted.controller.restapi.ControllerContainerTest;
import com.yahoo.vespa.hosted.controller.versions.NodeVersion;
import com.yahoo.vespa.hosted.controller.versions.VersionStatus;
import com.yahoo.vespa.hosted.controller.versions.VespaVersion;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * @author bratseth
 */
public class DeploymentApiTest extends ControllerContainerTest {

    private final static String responseFiles = "src/test/java/com/yahoo/vespa/hosted/controller/restapi/deployment/responses/";

    @Test
    void testDeploymentApi() {
        ContainerTester tester = new ContainerTester(container, responseFiles);
        DeploymentTester deploymentTester = new DeploymentTester(new ControllerTester(tester));
        Version version = Version.fromString("4.9");
        deploymentTester.controllerTester().upgradeSystem(version);
        ApplicationPackage multiInstancePackage = new ApplicationPackageBuilder()
                .instances("i1,i2")
                .region("us-west-1")
                .build();
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .region("us-west-1")
                .build();
        ApplicationPackage emptyPackage = new ApplicationPackageBuilder().instances("default")
                .allow(ValidationId.deploymentRemoval)
                .build();

        // Deploy application without any declared jobs on the oldest version.
        var oldAppWithoutDeployment = deploymentTester.newDeploymentContext("tenant4", "application4", "default");
        oldAppWithoutDeployment.submit().failDeployment(DeploymentContext.systemTest);
        oldAppWithoutDeployment.submit(emptyPackage);

        // System upgrades to 5.0 for the other applications.
        version = Version.fromString("5.0");
        deploymentTester.controllerTester().upgradeSystem(version);

        // 3 applications deploy on current system version.
        var failingApp = deploymentTester.newDeploymentContext("tenant1", "application1", "default");
        var productionApp = deploymentTester.newDeploymentContext("tenant2", "application2", "i1");
        var otherProductionApp = deploymentTester.newDeploymentContext("tenant2", "application2", "i2");
        var appWithoutDeployments = deploymentTester.newDeploymentContext("tenant3", "application3", "default");
        failingApp.submit(applicationPackage).deploy();
        productionApp.submit(multiInstancePackage).runJob(DeploymentContext.systemTest).runJob(DeploymentContext.stagingTest).runJob(DeploymentContext.productionUsWest1);
        otherProductionApp.runJob(DeploymentContext.productionUsWest1);

        // Deploy once so that job information is stored, then remove the deployment by submitting an empty deployment spec.
        appWithoutDeployments.submit(applicationPackage).deploy();
        appWithoutDeployments.submit(new ApplicationPackageBuilder().allow(ValidationId.deploymentRemoval).build());

        // New version released
        version = Version.fromString("5.1");
        deploymentTester.controllerTester().upgradeSystem(version);

        // Applications upgrade, 1/2 succeed
        deploymentTester.upgrader().maintain();
        deploymentTester.triggerJobs();
        productionApp.runJob(DeploymentContext.systemTest).runJob(DeploymentContext.stagingTest).runJob(DeploymentContext.productionUsWest1);
        failingApp.failDeployment(DeploymentContext.systemTest).failDeployment(DeploymentContext.stagingTest).timeOutConvergence(DeploymentContext.stagingTest);
        deploymentTester.upgrader().maintain();
        deploymentTester.triggerJobs();

        // Application fails application change
        productionApp.submit(multiInstancePackage).failDeployment(DeploymentContext.systemTest);

        tester.controller().updateVersionStatus(censorConfigServers(VersionStatus.compute(tester.controller())));
        tester.assertResponse(operatorRequest("http://localhost:8080/deployment/v1/"), new File("root.json"));
    }

    private VersionStatus censorConfigServers(VersionStatus versionStatus) {
        List<VespaVersion> censored = new ArrayList<>();
        for (VespaVersion version : versionStatus.versions()) {
            if (version.nodeVersions().size() > 0) {
                version = new VespaVersion(version.versionNumber(),
                                           version.releaseCommit(),
                                           version.committedAt(),
                                           version.isControllerVersion(),
                                           version.isSystemVersion(),
                                           version.isReleased(),
                                           List.of(new NodeVersion(HostName.of("config1.test"), ZoneId.defaultId(), version.versionNumber(), version.versionNumber(), Optional.empty()),
                                                   new NodeVersion(HostName.of("config2.test"), ZoneId.defaultId(), version.versionNumber(), version.versionNumber(), Optional.empty())),
                                           version.confidence()
                );
            }
            censored.add(version);
        }
        return new VersionStatus(censored);
    }

}
