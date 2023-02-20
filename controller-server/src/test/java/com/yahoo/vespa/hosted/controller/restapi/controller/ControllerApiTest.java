// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.controller;

import com.yahoo.application.container.handler.Request;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.security.KeyId;
import com.yahoo.security.KeyUtils;
import com.yahoo.security.SecretSharedKey;
import com.yahoo.security.SharedKeyGenerator;
import com.yahoo.security.SharedKeyResealingSession;
import com.yahoo.slime.SlimeUtils;
import com.yahoo.test.ManualClock;
import com.yahoo.vespa.flags.InMemoryFlagSource;
import com.yahoo.vespa.flags.PermanentFlags;
import com.yahoo.vespa.hosted.controller.api.identifiers.DeploymentId;
import com.yahoo.vespa.hosted.controller.api.integration.athenz.MockAccessControlService;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.Application;
import com.yahoo.vespa.hosted.controller.api.integration.resource.ResourceSnapshot;
import com.yahoo.vespa.hosted.controller.auditlog.AuditLogger;
import com.yahoo.vespa.hosted.controller.integration.NodeRepositoryMock;
import com.yahoo.vespa.hosted.controller.integration.SecretStoreMock;
import com.yahoo.vespa.hosted.controller.restapi.ContainerTester;
import com.yahoo.vespa.hosted.controller.restapi.ControllerContainerTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.interfaces.XECPrivateKey;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static com.yahoo.security.ArrayUtils.hex;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author bratseth
 */
public class ControllerApiTest extends ControllerContainerTest {

    private static final String responseFiles = "src/test/java/com/yahoo/vespa/hosted/controller/restapi/controller/responses/";

    private ContainerTester tester;

    @BeforeEach
    public void before() {
        tester = new ContainerTester(container, responseFiles);
    }

    @Test
    void testControllerApi() {
        tester.assertResponse(authenticatedRequest("http://localhost:8080/controller/v1/", "", Request.Method.GET), new File("root.json"));

        ((InMemoryFlagSource) tester.controller().flagSource()).withListFlag(PermanentFlags.INACTIVE_MAINTENANCE_JOBS.id(), List.of("DeploymentExpirer"), String.class);

        // GET a list of all maintenance jobs
        tester.assertResponse(authenticatedRequest("http://localhost:8080/controller/v1/maintenance/", "", Request.Method.GET),
                              new File("maintenance.json"));
    }

    @Test
    void testStats() {
        var mock = (NodeRepositoryMock) tester.controller().serviceRegistry().configServer().nodeRepository();
        mock.putApplication(ZoneId.from("prod", "us-west-1"),
                new Application(ApplicationId.fromFullString("t1.a1.i1"), List.of()));
        mock.putApplication(ZoneId.from("prod", "us-west-1"),
                new Application(ApplicationId.fromFullString("t2.a2.i2"), List.of()));
        mock.putApplication(ZoneId.from("prod", "us-east-3"),
                new Application(ApplicationId.fromFullString("t1.a1.i1"), List.of()));

        tester.assertResponse(authenticatedRequest("http://localhost:8080/controller/v1/stats", "", Request.Method.GET),
                new File("stats.json"));
    }

    @Test
    void testUpgraderApi() {
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

        // Override confidence
        tester.assertResponse(
                operatorRequest("http://localhost:8080/controller/v1/jobs/upgrader/confidence/6.42", "broken", Request.Method.POST),
                "{\"upgradesPerMinute\":42.0,\"confidenceOverrides\":[{\"6.42.0\":\"broken\"}]}",
                200);

        // Override confidence for another version
        tester.assertResponse(
                operatorRequest("http://localhost:8080/controller/v1/jobs/upgrader/confidence/6.43", " broken ", Request.Method.POST),
                "{\"upgradesPerMinute\":42.0,\"confidenceOverrides\":[{\"6.42.0\":\"broken\"},{\"6.43.0\":\"broken\"}]}",
                200);

        // Remove first override
        tester.assertResponse(
                operatorRequest("http://localhost:8080/controller/v1/jobs/upgrader/confidence/6.42", "", Request.Method.DELETE),
                "{\"upgradesPerMinute\":42.0,\"confidenceOverrides\":[{\"6.43.0\":\"broken\"}]}",
                200);

        assertFalse(tester.controller().auditLogger().readLog().entries().isEmpty(), "Actions are logged to audit log");
    }

    @Test
    void testAuditLogApi() {
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
    void testMeteringApi() {
        ApplicationId applicationId = ApplicationId.from("tenant", "app", "instance");
        Instant timestamp = Instant.ofEpochMilli(123456789);
        ZoneId zoneId = ZoneId.defaultId();
        List<ResourceSnapshot> snapshots = List.of(
                new ResourceSnapshot(applicationId, 12, 48, 1200, NodeResources.Architecture.arm64, timestamp, zoneId),
                new ResourceSnapshot(applicationId, 24, 96, 2400,  NodeResources.Architecture.x86_64, timestamp, zoneId)
        );
        tester.controller().serviceRegistry().resourceDatabase().writeResourceSnapshots(snapshots);
        tester.assertResponse(
                operatorRequest("http://localhost:8080/controller/v1/metering/tenant/tenantName/month/2020-02", "", Request.Method.GET),
                new File("metering.json")
        );
    }

    @Test
    void testApproveMembership() {
        ApplicationId applicationId = ApplicationId.from("tenant", "app", "instance");
        DeploymentId deployment = new DeploymentId(applicationId, ZoneId.defaultId());
        String requestBody = "{\n" +
                " \"applicationId\": \"" + deployment.applicationId().serializedForm() + "\",\n" +
                " \"zone\": \"" + deployment.zoneId().value() + "\"\n" +
                "}";

        MockAccessControlService accessControlService = (MockAccessControlService) tester.serviceRegistry().accessControlService();
        tester.assertResponse(operatorRequest("http://localhost:8080/controller/v1/access/requests/" + hostedOperator.getName(), requestBody, Request.Method.POST),
                "{\"message\":\"Unable to approve membership request\"}", 400);

        accessControlService.addPendingMember(hostedOperator);
        tester.assertResponse(operatorRequest("http://localhost:8080/controller/v1/access/requests/" + hostedOperator.getName(), requestBody, Request.Method.POST),
                "{\"message\":\"Unable to approve membership request\"}", 400);

        tester.controller().supportAccess().allow(deployment, tester.controller().clock().instant().plus(Duration.ofHours(1)), "tenantx");
        tester.assertResponse(operatorRequest("http://localhost:8080/controller/v1/access/requests/" + hostedOperator.getName(), requestBody, Request.Method.POST),
                "{\"members\":[\"user.alice\"]}");
    }

    private SharedKeyResealingSession.ResealingResponse extractResealingResponseFromJsonResponse(String json) {
        var cursor = SlimeUtils.jsonToSlime(json).get();
        var responseField = cursor.field("resealResponse");
        if (!responseField.valid()) {
            fail("No 'resealResponse' field in JSON response");
        }
        return SharedKeyResealingSession.ResealingResponse.fromSerializedString(responseField.asString());
    }

    private record ResealingTestData(SharedKeyResealingSession.ResealingRequest resealingRequest,
                                     SharedKeyResealingSession session,
                                     SecretSharedKey originalSecretSharedKey,
                                     KeyPair originalReceiverKeyPair) {}

    private static ResealingTestData createResealingRequestData(String keyIdStr) {
        var receiverKeyPair = KeyUtils.generateX25519KeyPair();
        var keyId = KeyId.ofString(keyIdStr);
        var sharedKey = SharedKeyGenerator.generateForReceiverPublicKey(receiverKeyPair.getPublic(), keyId);

        var session = SharedKeyResealingSession.newEphemeralSession();
        var resealRequest = session.resealingRequestFor(sharedKey.sealedSharedKey());
        return new ResealingTestData(resealRequest, session, sharedKey, receiverKeyPair);
    }

    private static String requestJsonOf(ResealingTestData reqData) {
        return "{\"resealRequest\":\"%s\"}".formatted(reqData.resealingRequest.toSerializedString());
    }

    @Test
    void decryption_token_reseal_request_succeeds_when_matching_versioned_key_found() {
        var reqData = createResealingRequestData("a.really.cool.key.123"); // Must match key name in config
        var secret = hex(reqData.originalSecretSharedKey.secretKey().getEncoded());

        var secretStore = (SecretStoreMock)tester.controller().secretStore();
        secretStore.setSecret("a.really.cool.key", KeyUtils.toBase58EncodedX25519PrivateKey((XECPrivateKey)reqData.originalReceiverKeyPair.getPrivate()), 123);

        tester.assertResponse(
                () -> operatorRequest("http://localhost:8080/controller/v1/access/cores/reseal", requestJsonOf(reqData), Request.Method.POST),
                (responseJson) -> {
                    var resealResponse = extractResealingResponseFromJsonResponse(responseJson.getBodyAsString());
                    var myShared = reqData.session.openResealingResponse(resealResponse);
                    assertEquals(secret, hex(reqData.originalSecretSharedKey.secretKey().getEncoded()));
                },
                200);
    }

    @Test
    void decryption_token_reseal_request_fails_when_unexpected_key_name_is_supplied() {
        var reqData = createResealingRequestData("a.really.cool.but.non.existing.key.123");
        tester.assertResponse(
                () -> operatorRequest("http://localhost:8080/controller/v1/access/cores/reseal", requestJsonOf(reqData), Request.Method.POST),
                "{\"error-code\":\"BAD_REQUEST\",\"message\":\"Token is not generated for the expected key\"}",
                400);
    }

    @Test
    void secret_key_lookup_does_not_use_key_id_provided_in_user_supplied_token() {
        var reqData = createResealingRequestData("a.sneaky.key.123");
        var secretStore = (SecretStoreMock)tester.controller().secretStore();
        // Token key ID is technically valid, but should not be used. Only config should be obeyed.
        secretStore.setSecret("a.sneaky.key", KeyUtils.toBase58EncodedX25519PrivateKey((XECPrivateKey)reqData.originalReceiverKeyPair.getPrivate()), 123);

        tester.assertResponse(
                () -> operatorRequest("http://localhost:8080/controller/v1/access/cores/reseal", requestJsonOf(reqData), Request.Method.POST),
                "{\"error-code\":\"BAD_REQUEST\",\"message\":\"Token is not generated for the expected key\"}",
                400);
    }

    @Test
    void decryption_token_reseal_request_fails_when_request_payload_is_missing_or_bogus() {
        tester.assertResponse(
                () -> operatorRequest("http://localhost:8080/controller/v1/access/cores/reseal", "{}", Request.Method.POST),
                "{\"error-code\":\"BAD_REQUEST\",\"message\":\"Expected field \\\"resealRequest\\\" in request\"}",
                400);
        // TODO this error message is technically an implementation detail...
        tester.assertResponse(
                () -> operatorRequest("http://localhost:8080/controller/v1/access/cores/reseal",
                        "{\"resealRequest\":\"five badgers destroying a flowerbed\"}", Request.Method.POST),
                "{\"error-code\":\"BAD_REQUEST\",\"message\":\"Input character not part of codec alphabet\"}",
                400);
    }

    @Test
    void decryption_token_reseal_request_fails_when_key_id_does_not_conform_to_expected_form() {
        tester.assertResponse(
                () -> operatorRequest("http://localhost:8080/controller/v1/access/cores/reseal",
                        requestJsonOf(createResealingRequestData("a-really-cool-key")), Request.Method.POST),
                "{\"error-code\":\"BAD_REQUEST\",\"message\":\"Key ID is not of the form 'name.version'\"}",
                400);
        tester.assertResponse(
                () -> operatorRequest("http://localhost:8080/controller/v1/access/cores/reseal",
                        requestJsonOf(createResealingRequestData("a.really.cool.key.123asdf")), Request.Method.POST),
                "{\"error-code\":\"BAD_REQUEST\",\"message\":\"Key version is not a valid integer\"}",
                400);
        tester.assertResponse(
                () -> operatorRequest("http://localhost:8080/controller/v1/access/cores/reseal",
                        requestJsonOf(createResealingRequestData("a.really.cool.key.")), Request.Method.POST),
                "{\"error-code\":\"BAD_REQUEST\",\"message\":\"Key version is not a valid integer\"}",
                400);
        tester.assertResponse(
                () -> operatorRequest("http://localhost:8080/controller/v1/access/cores/reseal",
                        requestJsonOf(createResealingRequestData("a.really.cool.key.-123")), Request.Method.POST),
                "{\"error-code\":\"BAD_REQUEST\",\"message\":\"Key version is out of range\"}",
                400);
        tester.assertResponse(
                () -> operatorRequest("http://localhost:8080/controller/v1/access/cores/reseal",
                        requestJsonOf(createResealingRequestData("a.really.cool.key.%d".formatted((long)Integer.MAX_VALUE + 1))), Request.Method.POST),
                "{\"error-code\":\"BAD_REQUEST\",\"message\":\"Key version is not a valid integer\"}",
                400);
    }

}
