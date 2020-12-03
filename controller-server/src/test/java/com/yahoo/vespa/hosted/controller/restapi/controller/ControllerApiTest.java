// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.controller;

import com.yahoo.application.container.handler.Request;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.test.ManualClock;
import com.yahoo.vespa.flags.InMemoryFlagSource;
import com.yahoo.vespa.flags.PermanentFlags;
import com.yahoo.vespa.hosted.controller.api.integration.resource.ResourceSnapshot;
import com.yahoo.vespa.hosted.controller.auditlog.AuditLogger;
import com.yahoo.vespa.hosted.controller.restapi.ContainerTester;
import com.yahoo.vespa.hosted.controller.restapi.ControllerContainerTest;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.Assert.assertFalse;

/**
 * @author bratseth
 */
public class ControllerApiTest extends ControllerContainerTest {

    private static final String responseFiles = "src/test/java/com/yahoo/vespa/hosted/controller/restapi/controller/responses/";

    private ContainerTester tester;

    @Before
    public void before() {
        tester = new ContainerTester(container, responseFiles);
    }

    @Test
    public void testControllerApi() {
        tester.assertResponse(authenticatedRequest("http://localhost:8080/controller/v1/", "", Request.Method.GET), new File("root.json"));

        ((InMemoryFlagSource) tester.controller().flagSource()).withListFlag(PermanentFlags.INACTIVE_MAINTENANCE_JOBS.id(), List.of("DeploymentExpirer"), String.class);

        // GET a list of all maintenance jobs
        tester.assertResponse(authenticatedRequest("http://localhost:8080/controller/v1/maintenance/", "", Request.Method.GET),
                              new File("maintenance.json"));
    }

    @Test
    public void testUpgraderApi() {
        // Get current configuration
        tester.assertResponse(authenticatedRequest("http://localhost:8080/controller/v1/jobs/upgrader", "", Request.Method.GET),
                              "{\"upgradesPerMinute\":0.125,\"confidenceOverrides\":[]}",
                              200);

        // Set invalid configuration
        tester.assertResponse(
                operatorRequest("http://localhost:8080/controller/v1/jobs/upgrader", "{\"upgradesPerMinute\":-1}", Request.Method.PATCH),
                "{\"error-code\":\"BAD_REQUEST\",\"message\":\"Upgrades per minute must be >= 0, got -1.0\"}",
                400);

        // Ignores unrecognized field
        tester.assertResponse(
                operatorRequest("http://localhost:8080/controller/v1/jobs/upgrader", "{\"foo\":\"bar\"}", Request.Method.PATCH),
                "{\"error-code\":\"BAD_REQUEST\",\"message\":\"No such modifiable field(s)\"}",
                400);

        // Set upgrades per minute
        tester.assertResponse(
                operatorRequest("http://localhost:8080/controller/v1/jobs/upgrader", "{\"upgradesPerMinute\":42.0}", Request.Method.PATCH),
                "{\"upgradesPerMinute\":42.0,\"confidenceOverrides\":[]}",
                200);

        // Set target major version
        tester.assertResponse(
                operatorRequest("http://localhost:8080/controller/v1/jobs/upgrader", "{\"targetMajorVersion\":6}", Request.Method.PATCH),
                "{\"upgradesPerMinute\":42.0,\"targetMajorVersion\":6,\"confidenceOverrides\":[]}",
                200);

        // Clear target major version
        tester.assertResponse(
                operatorRequest("http://localhost:8080/controller/v1/jobs/upgrader", "{\"targetMajorVersion\":null}", Request.Method.PATCH),
                "{\"upgradesPerMinute\":42.0,\"confidenceOverrides\":[]}",
                200);

        // Override confidence
        tester.assertResponse(
                operatorRequest("http://localhost:8080/controller/v1/jobs/upgrader/confidence/6.42", "broken", Request.Method.POST),
                "{\"upgradesPerMinute\":42.0,\"confidenceOverrides\":[{\"6.42\":\"broken\"}]}",
                200);

        // Override confidence for another version
        tester.assertResponse(
                operatorRequest("http://localhost:8080/controller/v1/jobs/upgrader/confidence/6.43", "broken", Request.Method.POST),
                "{\"upgradesPerMinute\":42.0,\"confidenceOverrides\":[{\"6.42\":\"broken\"},{\"6.43\":\"broken\"}]}",
                200);

        // Remove first override
        tester.assertResponse(
                operatorRequest("http://localhost:8080/controller/v1/jobs/upgrader/confidence/6.42", "", Request.Method.DELETE),
                "{\"upgradesPerMinute\":42.0,\"confidenceOverrides\":[{\"6.43\":\"broken\"}]}",
                200);

        assertFalse("Actions are logged to audit log", tester.controller().auditLogger().readLog().entries().isEmpty());
    }

    @Test
    public void testAuditLogApi() {
        ManualClock clock = new ManualClock(Instant.parse("2019-03-01T12:13:14.00Z"));
        AuditLogger logger = new AuditLogger(tester.controller().curator(), clock);

        // Log some operator actions
        HttpRequest req1 = HttpRequest.createTestRequest(
                "http://localhost:8080/controller/v1/maintenance/inactive/DeploymentExpirer",
                com.yahoo.jdisc.http.HttpRequest.Method.POST
        );
        req1.getJDiscRequest().setUserPrincipal(() -> "operator1");
        logger.log(req1);

        clock.advance(Duration.ofHours(2));
        HttpRequest req2 = HttpRequest.createTestRequest(
                "http://localhost:8080/controller/v1/jobs/upgrader/confidence/6.42",
                com.yahoo.jdisc.http.HttpRequest.Method.POST,
                new ByteArrayInputStream("broken".getBytes(StandardCharsets.UTF_8))
        );
        req2.getJDiscRequest().setUserPrincipal(() -> "operator2");
        logger.log(req2);

        // Verify log
        tester.assertResponse(authenticatedRequest("http://localhost:8080/controller/v1/auditlog/"), new File("auditlog.json"));
    }

    @Test
    public void testMeteringApi() {
        ApplicationId applicationId = ApplicationId.from("tenant", "app", "instance");
        Instant timestamp = Instant.ofEpochMilli(123456789);
        ZoneId zoneId = ZoneId.defaultId();
        List<ResourceSnapshot> snapshots = List.of(
                new ResourceSnapshot(applicationId, 12,48,1200, timestamp, zoneId),
                new ResourceSnapshot(applicationId, 24, 96,2400, timestamp, zoneId)
        );
        tester.controller().serviceRegistry().meteringService().consume(snapshots);
        tester.assertResponse(
                operatorRequest("http://localhost:8080/controller/v1/metering/tenant/tenantName/month/2020-02", "", Request.Method.GET),
                new File("metering.json")
        );
    }

}
