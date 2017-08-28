// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.deployment;

import com.google.common.collect.ImmutableSet;
import com.yahoo.application.container.handler.Request;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.Zone;
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
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static com.yahoo.vespa.hosted.controller.application.DeploymentJobs.JobType.component;
import static com.yahoo.vespa.hosted.controller.application.DeploymentJobs.JobType.stagingTest;
import static com.yahoo.vespa.hosted.controller.application.DeploymentJobs.JobType.systemTest;

/**
 * @author bratseth
 */
public class DeploymentApiTest extends ControllerContainerTest {

    private final static String responseFiles = "src/test/java/com/yahoo/vespa/hosted/controller/restapi/deployment/responses/";

    @Test
    public void testDeploymentApi() throws IOException {
        ContainerControllerTester tester = new ContainerControllerTester(container, responseFiles);
        tester.containerTester().updateSystemVersion();
        long projectId = 11;
        Application app = tester.createApplication();
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .environment(Environment.prod)
                .region("corp-us-east-1")
                .build();
        tester.notifyJobCompletion(app.id(), projectId, true, component);
        tester.deploy(app, applicationPackage, new Zone(Environment.test, RegionName.from("us-east-1")), projectId);
        tester.notifyJobCompletion(app.id(), projectId, true, systemTest);
        tester.deploy(app, applicationPackage, new Zone(Environment.staging, RegionName.from("us-east-3")), projectId);
        tester.notifyJobCompletion(app.id(), projectId, false, stagingTest);

        tester.controller().updateVersionStatus(censorConfigServers(VersionStatus.compute(tester.controller()), 
                                                                    tester.controller()));
        tester.assertResponse(new Request("http://localhost:8080/deployment/v1/"),
                              new File("root.json"));
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
                                           controller);
            censored.add(version);
        }
        return new VersionStatus(censored);
    }

}
