// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.os;

import com.yahoo.application.container.handler.Request;
import com.yahoo.config.provision.CloudName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.zone.UpgradePolicy;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.athenz.api.AthenzIdentity;
import com.yahoo.vespa.athenz.api.AthenzUser;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.Node;
import com.yahoo.vespa.hosted.controller.application.SystemApplication;
import com.yahoo.vespa.hosted.controller.integration.ConfigServerMock;
import com.yahoo.vespa.hosted.controller.integration.NodeRepositoryMock;
import com.yahoo.vespa.hosted.controller.integration.ZoneRegistryMock;
import com.yahoo.vespa.hosted.controller.maintenance.JobControl;
import com.yahoo.vespa.hosted.controller.maintenance.Maintainer;
import com.yahoo.vespa.hosted.controller.maintenance.OsUpgrader;
import com.yahoo.vespa.hosted.controller.restapi.ContainerControllerTester;
import com.yahoo.vespa.hosted.controller.restapi.ControllerContainerTest;
import com.yahoo.vespa.hosted.controller.versions.OsVersionStatus;
import org.intellij.lang.annotations.Language;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.time.Duration;
import java.util.List;

import static org.junit.Assert.assertFalse;

/**
 * @author mpolden
 */
public class OsApiTest extends ControllerContainerTest {

    private static final String responses = "src/test/java/com/yahoo/vespa/hosted/controller/restapi/os/responses/";
    private static final AthenzIdentity operator = AthenzUser.fromUserId("operatorUser");
    private static final CloudName cloud1 = CloudName.from("cloud1");
    private static final CloudName cloud2 = CloudName.from("cloud2");
    private static final ZoneId zone1 = ZoneId.from("prod", "us-east-3", cloud1.value());
    private static final ZoneId zone2 = ZoneId.from("prod", "us-west-1", cloud1.value());
    private static final ZoneId zone3 = ZoneId.from("prod", "eu-west-1", cloud2.value());

    private ContainerControllerTester tester;
    private List<OsUpgrader> osUpgraders;

    @Before
    public void before() {
        tester = new ContainerControllerTester(container, responses);
        addUserToHostedOperatorRole(operator);
        zoneRegistryMock().setSystemName(SystemName.cd)
                          .setZones(zone1, zone2, zone3)
                          .setOsUpgradePolicy(cloud1, UpgradePolicy.create().upgrade(zone1).upgrade(zone2))
                          .setOsUpgradePolicy(cloud2, UpgradePolicy.create().upgrade(zone3));
        osUpgraders = List.of(
                new OsUpgrader(tester.controller(), Duration.ofDays(1),
                               new JobControl(tester.controller().curator()),
                               cloud1),
                new OsUpgrader(tester.controller(), Duration.ofDays(1),
                               new JobControl(tester.controller().curator()),
                               cloud2));
    }

    @Test
    public void test_api() {
        // No versions available yet
        assertResponse(new Request("http://localhost:8080/os/v1/"), "{\"versions\":[]}", 200);

        // All nodes are initially on empty version
        upgradeAndUpdateStatus();
        assertFile(new Request("http://localhost:8080/os/v1/"), "versions-initial.json");

        // Upgrade OS to a different version in each cloud
        assertResponse(new Request("http://localhost:8080/os/v1/", "{\"version\": \"7.5.2\", \"cloud\": \"cloud1\"}", Request.Method.PATCH),
                       "{\"message\":\"Set target OS version for cloud 'cloud1' to 7.5.2\"}", 200);
        assertResponse(new Request("http://localhost:8080/os/v1/", "{\"version\": \"8.2.1\", \"cloud\": \"cloud2\"}", Request.Method.PATCH),
                       "{\"message\":\"Set target OS version for cloud 'cloud2' to 8.2.1\"}", 200);

        // Status is updated after some zones are upgraded
        upgradeAndUpdateStatus();
        completeUpgrade(zone1);
        assertFile(new Request("http://localhost:8080/os/v1/"), "versions-partially-upgraded.json");

        // All zones are upgraded
        upgradeAndUpdateStatus();
        completeUpgrade(zone2, zone3);
        assertFile(new Request("http://localhost:8080/os/v1/"), "versions-all-upgraded.json");

        // Downgrade with force is permitted
        assertResponse(new Request("http://localhost:8080/os/v1/", "{\"version\": \"7.5.1\", \"cloud\": \"cloud1\", \"force\": true}", Request.Method.PATCH),
                       "{\"message\":\"Set target OS version for cloud 'cloud1' to 7.5.1\"}", 200);

        // Error: Missing field 'cloud' or 'version'
        assertResponse(new Request("http://localhost:8080/os/v1/", "{\"version\": \"7.6\"}", Request.Method.PATCH),
                       "{\"error-code\":\"BAD_REQUEST\",\"message\":\"Fields 'version' and 'cloud' are required\"}", 400);
        assertResponse(new Request("http://localhost:8080/os/v1/", "{\"cloud\": \"cloud1\"}", Request.Method.PATCH),
                       "{\"error-code\":\"BAD_REQUEST\",\"message\":\"Fields 'version' and 'cloud' are required\"}", 400);

        // Error: Invalid versions
        assertResponse(new Request("http://localhost:8080/os/v1/", "{\"version\": null, \"cloud\": \"cloud1\"}", Request.Method.PATCH),
                       "{\"error-code\":\"BAD_REQUEST\",\"message\":\"Invalid version '0.0.0'\"}", 400);
        assertResponse(new Request("http://localhost:8080/os/v1/", "{\"version\": \"foo\", \"cloud\": \"cloud1\"}", Request.Method.PATCH),
                       "{\"error-code\":\"BAD_REQUEST\",\"message\":\"Invalid version 'foo': For input string: \\\"foo\\\"\"}", 400);

        // Error: Invalid cloud
        assertResponse(new Request("http://localhost:8080/os/v1/", "{\"version\": \"7.6\", \"cloud\": \"foo\"}", Request.Method.PATCH),
                       "{\"error-code\":\"BAD_REQUEST\",\"message\":\"Cloud 'foo' does not exist in this system\"}", 400);

        // Error: Downgrade OS
        assertResponse(new Request("http://localhost:8080/os/v1/", "{\"version\": \"7.4.1\", \"cloud\": \"cloud1\"}", Request.Method.PATCH),
                       "{\"error-code\":\"BAD_REQUEST\",\"message\":\"Cannot downgrade cloud 'cloud1' to version 7.4.1\"}", 400);

        // Request firmware checks in all zones.
        assertResponse(new Request("http://localhost:8080/os/v1/firmware/", "", Request.Method.POST),
                       "{\"message\":\"Requested firmware checks in prod.us-east-3, prod.us-west-1, prod.eu-west-1.\"}", 200);

        // Cancel firmware checks in all prod zones.
        assertResponse(new Request("http://localhost:8080/os/v1/firmware/prod/", "", Request.Method.DELETE),
                       "{\"message\":\"Cancelled firmware checks in prod.us-east-3, prod.us-west-1, prod.eu-west-1.\"}", 200);

        // Request firmware checks in prod.us-east-3.
        assertResponse(new Request("http://localhost:8080/os/v1/firmware/prod/us-east-3", "", Request.Method.POST),
                       "{\"message\":\"Requested firmware checks in prod.us-east-3.\"}", 200);

        // Error: Cancel firmware checks in an empty set of zones.
        assertResponse(new Request("http://localhost:8080/os/v1/firmware/dev/", "", Request.Method.DELETE),
                       "{\"error-code\":\"NOT_FOUND\",\"message\":\"No zones at path '/os/v1/firmware/dev'\"}", 404);

        assertFalse("Actions are logged to audit log", tester.controller().auditLogger().readLog().entries().isEmpty());
    }

    private void upgradeAndUpdateStatus() {
        osUpgraders.forEach(Maintainer::run);
        updateVersionStatus();
    }

    private void updateVersionStatus() {
        tester.controller().updateOsVersionStatus(OsVersionStatus.compute(tester.controller()));
    }

    private void completeUpgrade(ZoneId... zones) {
        for (ZoneId zone : zones) {
            for (SystemApplication application : SystemApplication.all()) {
                for (Node node : nodeRepository().list(zone, application.id())) {
                    nodeRepository().putByHostname(zone, new Node(
                            node.hostname(), node.state(), node.type(), node.owner(), node.currentVersion(),
                            node.wantedVersion(), node.wantedOsVersion(), node.wantedOsVersion(), node.serviceState(),
                            node.restartGeneration(), node.wantedRestartGeneration(), node.rebootGeneration(),
                            node.wantedRebootGeneration(), node.canonicalFlavor(), node.clusterId(), node.clusterType()));
                }
            }
        }
        updateVersionStatus();
    }

    private ZoneRegistryMock zoneRegistryMock() {
        return (ZoneRegistryMock) tester.containerTester().container().components()
                                        .getComponent(ZoneRegistryMock.class.getName());
    }

    private NodeRepositoryMock nodeRepository() {
        return ((ConfigServerMock) tester.containerTester().container().components()
                                         .getComponent(ConfigServerMock.class.getName())).nodeRepository();
    }

    private void assertResponse(Request request, @Language("JSON") String body, int statusCode) {
        addIdentityToRequest(request, operator);
        tester.assertResponse(request, body, statusCode);
    }

    private void assertFile(Request request, String filename) {
        addIdentityToRequest(request, operator);
        tester.assertResponse(request, new File(filename));
    }

}
