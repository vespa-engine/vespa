// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.deployment;

import com.google.common.collect.ImmutableSet;
import com.yahoo.component.Version;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.RegionName;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.integration.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.application.ApplicationPackage;
import com.yahoo.vespa.hosted.controller.deployment.ApplicationPackageBuilder;
import com.yahoo.vespa.hosted.controller.restapi.ContainerControllerTester;
import com.yahoo.vespa.hosted.controller.restapi.ControllerContainerTest;
import com.yahoo.vespa.hosted.controller.versions.VersionStatus;
import com.yahoo.vespa.hosted.controller.versions.VespaVersion;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static com.yahoo.vespa.hosted.controller.application.DeploymentJobs.JobType.component;
import static com.yahoo.vespa.hosted.controller.application.DeploymentJobs.JobType.productionCorpUsEast1;
import static com.yahoo.vespa.hosted.controller.application.DeploymentJobs.JobType.stagingTest;
import static com.yahoo.vespa.hosted.controller.application.DeploymentJobs.JobType.systemTest;

/**
 * @author bratseth
 */
public class DeploymentApiTest extends ControllerContainerTest {

    private final static String responseFiles = "src/test/java/com/yahoo/vespa/hosted/controller/restapi/deployment/responses/";

    private ContainerControllerTester tester;

    @Before
    public void before() {
        tester = new ContainerControllerTester(container, responseFiles);
    }

    @Test
    public void testDeploymentApi() throws IOException {
        ContainerControllerTester tester = new ContainerControllerTester(container, responseFiles);
        Version version = Version.fromString("5.0");
        tester.containerTester().updateSystemVersion(version);
        long projectId = 11;
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .environment(Environment.prod)
                .region("corp-us-east-1")
                .build();

        // 3 applications deploy on current system version
        Application failingApplication = tester.createApplication("domain1", "tenant1",
                                                                  "application1");
        Application productionApplication = tester.createApplication("domain2", "tenant2",
                                                                     "application2");
        Application applicationWithoutDeployment = tester.createApplication("domain3", "tenant3",
                                                                             "application3");
        deployCompletely(failingApplication, applicationPackage, projectId, true);
        deployCompletely(productionApplication, applicationPackage, projectId, true);

        // Deploy once so that job information is stored, then remove the deployment
        deployCompletely(applicationWithoutDeployment, applicationPackage, projectId, true);
        tester.controller().applications().deactivate(applicationWithoutDeployment,
                                                      ZoneId.from("prod", "corp-us-east-1"));

        // New version released
        version = Version.fromString("5.1");
        tester.containerTester().updateSystemVersion(version);

        // Applications upgrade, 1/2 succeed
        tester.upgrader().maintain();
        deployCompletely(failingApplication, applicationPackage, projectId, false);
        deployCompletely(productionApplication, applicationPackage, projectId, true);

        tester.controller().updateVersionStatus(censorConfigServers(VersionStatus.compute(tester.controller()),
                                                                    tester.controller()));
        tester.assertResponse(authenticatedRequest("http://localhost:8080/deployment/v1/"), new File("root.json"));
    }

    private VersionStatus censorConfigServers(VersionStatus versionStatus, Controller controller) {
        List<VespaVersion> censored = new ArrayList<>();
        for (VespaVersion version : versionStatus.versions()) {
            if ( ! version.configServerHostnames().isEmpty())
                version = new VespaVersion(version.statistics(), 
                                           version.releaseCommit(), 
                                           version.releasedAt(), 
                                           version.isCurrentSystemVersion(), 
                                           ImmutableSet.of("config1.test", "config2.test"),
                                           VespaVersion.confidenceFrom(version.statistics(), controller)
                );
            censored.add(version);
        }
        return new VersionStatus(censored);
    }

    private void deployCompletely(Application application, ApplicationPackage applicationPackage, long projectId,
                                  boolean success) {
        tester.notifyJobCompletion(application.id(), projectId, true, component);
        tester.deploy(application, applicationPackage, ZoneId.from(Environment.test, RegionName.from("us-east-1")),
                      projectId);
        tester.notifyJobCompletion(application.id(), projectId, true, systemTest);
        tester.deploy(application, applicationPackage, ZoneId.from(Environment.staging, RegionName.from("us-east-3")),
                      projectId);
        tester.notifyJobCompletion(application.id(), projectId, success, stagingTest);
        if (success) {
            tester.deploy(application, applicationPackage, ZoneId.from(Environment.prod,
                                                                       RegionName.from("corp-us-east-1")), projectId);
            tester.notifyJobCompletion(application.id(), projectId, true, productionCorpUsEast1);
        }
    }

}
