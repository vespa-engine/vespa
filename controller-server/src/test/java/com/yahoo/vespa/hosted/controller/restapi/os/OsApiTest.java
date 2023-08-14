// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.os;

import com.yahoo.application.container.handler.Request;
import com.yahoo.component.Version;
import com.yahoo.config.provision.CloudName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.zone.UpgradePolicy;
import com.yahoo.config.provision.zone.ZoneApi;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.athenz.api.AthenzIdentity;
import com.yahoo.vespa.athenz.api.AthenzUser;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.Node;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.NodeFilter;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.OsRelease;
import com.yahoo.vespa.hosted.controller.application.SystemApplication;
import com.yahoo.vespa.hosted.controller.integration.NodeRepositoryMock;
import com.yahoo.vespa.hosted.controller.integration.ZoneApiMock;
import com.yahoo.vespa.hosted.controller.maintenance.ControllerMaintainer;
import com.yahoo.vespa.hosted.controller.maintenance.OsUpgrader;
import com.yahoo.vespa.hosted.controller.restapi.ContainerTester;
import com.yahoo.vespa.hosted.controller.restapi.ControllerContainerTest;
import com.yahoo.vespa.hosted.controller.versions.OsVersionStatus;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * @author mpolden
 */
public class OsApiTest extends ControllerContainerTest {

    private static final String responses = "src/test/java/com/yahoo/vespa/hosted/controller/restapi/os/responses/";
    private static final AthenzIdentity operator = AthenzUser.fromUserId("operatorUser");
    private static final CloudName cloud1 = CloudName.from("cloud1");
    private static final CloudName cloud2 = CloudName.from("cloud2");
    private static final ZoneApi zone1 = ZoneApiMock.newBuilder().withId("prod.us-east-3").with(cloud1).build();
    private static final ZoneApi zone2 = ZoneApiMock.newBuilder().withId("prod.us-west-1").with(cloud1).build();
    private static final ZoneApi zone3 = ZoneApiMock.newBuilder().withId("prod.eu-west-1").with(cloud2).build();

    private ContainerTester tester;
    private List<OsUpgrader> osUpgraders;

    @Override
    protected SystemName system() {
        return SystemName.cd;
    }

    @BeforeEach
    public void before() {
        tester = new ContainerTester(container, responses);
        tester.serviceRegistry().clock().setInstant(Instant.ofEpochMilli(1234));
        addUserToHostedOperatorRole(operator);
        tester.serviceRegistry().zoneRegistry().setZones(zone1, zone2, zone3)
              .dynamicProvisioningIn(zone3)
              .setOsUpgradePolicy(cloud1, UpgradePolicy.builder().upgrade(zone1).upgrade(zone2).build())
              .setOsUpgradePolicy(cloud2, UpgradePolicy.builder().upgrade(zone3).build());
        tester.serviceRegistry().artifactRepository().addRelease(new OsRelease(Version.fromString("7.0"),
                                                                               OsRelease.Tag.latest,
                                                                               Instant.EPOCH));
        osUpgraders = List.of(
                new OsUpgrader(tester.controller(), Duration.ofDays(1),
                               cloud1),
                new OsUpgrader(tester.controller(), Duration.ofDays(1),
                               cloud2));
    }

    @Test
    void test_api() {
        // No versions available yet
        assertResponse(new Request("http://localhost:8080/os/v1/"), "{\"versions\":[]}", 200);

        // All nodes are initially on empty version
        upgradeAndUpdateStatus();

        // Upgrade OS to a different version in each cloud
        assertResponse(new Request("http://localhost:8080/os/v1/", "{\"version\": \"7.5.2\", \"cloud\": \"cloud1\"}", Request.Method.PATCH),
                "{\"message\":\"Set target OS version for cloud 'cloud1' to 7.5.2\"}", 200);
        assertResponse(new Request("http://localhost:8080/os/v1/", "{\"version\": \"8.2.1\", \"cloud\": \"cloud2\"}", Request.Method.PATCH),
                "{\"message\":\"Set target OS version for cloud 'cloud2' to 8.2.1\"}", 200);

        // Status is updated after some zones are upgraded
        upgradeAndUpdateStatus();
        completeUpgrade(zone1.getId());
        assertFile(new Request("http://localhost:8080/os/v1/"), "versions-partially-upgraded.json");

        // All zones are upgraded
        upgradeAndUpdateStatus();
        completeUpgrade(zone2.getId(), zone3.getId());
        assertFile(new Request("http://localhost:8080/os/v1/"), "versions-all-upgraded.json");

        // Downgrade with force is permitted
        assertResponse(new Request("http://localhost:8080/os/v1/", "{\"version\": \"8.2.0\", \"cloud\": \"cloud2\", \"force\": true}", Request.Method.PATCH),
                       "{\"message\":\"Set target OS version for cloud 'cloud2' to 8.2.0\"}", 200);

        // Clear target for a given cloud
        assertResponse(new Request("http://localhost:8080/os/v1/", "{\"version\": null, \"cloud\": \"cloud1\"}", Request.Method.PATCH),
                "{\"message\":\"Cleared target OS version for cloud 'cloud1'\"}", 200);

        // Pin/unpin a version
        assertResponse(new Request("http://localhost:8080/os/v1/", "{\"version\": \"7.5.2\", \"cloud\": \"cloud1\", \"pin\": true}", Request.Method.PATCH),
                       "{\"message\":\"Set target OS version for cloud 'cloud1' to 7.5.2 (pinned)\"}", 200);
        assertResponse(new Request("http://localhost:8080/os/v1/", "{\"version\": \"7.5.2\", \"cloud\": \"cloud1\", \"pin\": false}", Request.Method.PATCH),
                       "{\"message\":\"Set target OS version for cloud 'cloud1' to 7.5.2\"}", 200);
        assertResponse(new Request("http://localhost:8080/os/v1/", "{\"version\": \"7.5.2\", \"cloud\": \"cloud1\", \"pin\": true}", Request.Method.PATCH),
                       "{\"message\":\"Set target OS version for cloud 'cloud1' to 7.5.2 (pinned)\"}", 200);

        // Certify an OS and Vespa version pair
        assertResponse(new Request("http://localhost:8080/os/v1/certify/cloud1/7.5.2", "8.200.37", Request.Method.POST),
                       "{\"message\":\"Certified 7.5.2 in cloud cloud1 as compatible with Vespa version 8.200.37\"}", 200);
        assertResponse(new Request("http://localhost:8080/os/v1/certify/cloud2/7.5.2", "8.200.33", Request.Method.POST),
                       "{\"message\":\"Certified 7.5.2 in cloud cloud2 as compatible with Vespa version 8.200.33\"}", 200);
        assertResponse(new Request("http://localhost:8080/os/v1/certify/cloud1/7.5.2", "8.200.42", Request.Method.POST),
                       "{\"message\":\"7.5.2 is already certified in cloud cloud1 as compatible with Vespa version 8.200.37. Leaving certification unchanged\"}", 200);
        assertResponse(new Request("http://localhost:8080/os/v1/certify", "", Request.Method.GET),
                       """
[{"version":"7.5.2","cloud":"cloud1","vespaVersion":"8.200.37"},{"version":"7.5.2","cloud":"cloud2","vespaVersion":"8.200.33"}]""", 200);
        assertResponse(new Request("http://localhost:8080/os/v1/certify/cloud1/7.5.2", "", Request.Method.DELETE),
                       "{\"message\":\"Removed certification of 7.5.2 in cloud cloud1\"}", 200);

        // Error: Missing fields
        assertResponse(new Request("http://localhost:8080/os/v1/", "{\"version\": \"7.6\"}", Request.Method.PATCH),
                "{\"error-code\":\"BAD_REQUEST\",\"message\":\"Field 'cloud' is required\"}", 400);
        assertResponse(new Request("http://localhost:8080/os/v1/", "{\"cloud\": \"cloud1\"}", Request.Method.PATCH),
                "{\"error-code\":\"BAD_REQUEST\",\"message\":\"Field 'version' is required\"}", 400);

        // Error: Invalid versions
        assertResponse(new Request("http://localhost:8080/os/v1/", "{\"version\": \"0.0.0\", \"cloud\": \"cloud1\"}", Request.Method.PATCH),
                "{\"error-code\":\"BAD_REQUEST\",\"message\":\"Invalid version '0.0.0'\"}", 400);
        assertResponse(new Request("http://localhost:8080/os/v1/", "{\"version\": \"foo\", \"cloud\": \"cloud1\"}", Request.Method.PATCH),
                "{\"error-code\":\"BAD_REQUEST\",\"message\":\"Invalid version 'foo': Invalid version component in 'foo': For input string: \\\"foo\\\"\"}", 400);

        // Error: Invalid cloud
        assertResponse(new Request("http://localhost:8080/os/v1/", "{\"version\": \"7.6\", \"cloud\": \"foo\"}", Request.Method.PATCH),
                "{\"error-code\":\"BAD_REQUEST\",\"message\":\"Cloud 'foo' does not exist in this system\"}", 400);

        // Error: Downgrade OS
        assertResponse(new Request("http://localhost:8080/os/v1/", "{\"version\": \"7.4.1\", \"cloud\": \"cloud2\"}", Request.Method.PATCH),
                "{\"error-code\":\"BAD_REQUEST\",\"message\":\"Cannot downgrade cloud 'cloud2' to version 7.4.1: Missing 'force' parameter\"}", 400);

        // Error: Change a pinned version
        assertResponse(new Request("http://localhost:8080/os/v1/", "{\"version\": \"7.5.3\", \"cloud\": \"cloud1\"}", Request.Method.PATCH),
                       "{\"error-code\":\"BAD_REQUEST\",\"message\":\"Cannot upgrade cloud cloud1' to version 7.5.3: Current target is pinned. Add 'force' parameter to override\"}", 400);

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
                "{\"error-code\":\"NOT_FOUND\",\"message\":\"No zones at path '/os/v1/firmware/dev/'\"}", 404);

        // Error: Missing or invalid versions to certify
        assertResponse(new Request("http://localhost:8080/os/v1/certify/cloud1/7.5.2", "", Request.Method.POST),
                       "{\"error-code\":\"BAD_REQUEST\",\"message\":\"Missing Vespa version in request body\"}", 400);
        assertResponse(new Request("http://localhost:8080/os/v1/certify/cloud1/7.5.2", "foo", Request.Method.POST),
                       "{\"error-code\":\"BAD_REQUEST\",\"message\":\"Invalid version component in 'foo': For input string: \\\"foo\\\"\"}", 400);
        assertResponse(new Request("http://localhost:8080/os/v1/certify/cloud1/bar", "1.2.3", Request.Method.POST),
                       "{\"error-code\":\"BAD_REQUEST\",\"message\":\"Invalid version component in 'bar': For input string: \\\"bar\\\"\"}", 400);

        assertFalse(tester.controller().auditLogger().readLog().entries().isEmpty(), "Actions are logged to audit log");
    }

    private void upgradeAndUpdateStatus() {
        osUpgraders.forEach(ControllerMaintainer::run);
        updateVersionStatus();
    }

    private void updateVersionStatus() {
        tester.controller().os().updateStatus(OsVersionStatus.compute(tester.controller()));
    }

    private void completeUpgrade(ZoneId... zones) {
        for (ZoneId zone : zones) {
            for (SystemApplication application : SystemApplication.all()) {
                var targetVersion = nodeRepository().targetVersionsOf(zone).osVersion(application.nodeType());
                for (Node node : nodeRepository().list(zone, NodeFilter.all().applications(application.id()))) {
                    var version = targetVersion.orElse(node.wantedOsVersion());
                    nodeRepository().putNodes(zone, Node.builder(node).currentOsVersion(version).wantedOsVersion(version).build());
                }
            }
        }
        updateVersionStatus();
    }

    private NodeRepositoryMock nodeRepository() {
        return tester.serviceRegistry().configServerMock().nodeRepository();
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
