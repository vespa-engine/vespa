// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.certificate;

import com.yahoo.config.application.api.DeploymentSpec;
import com.yahoo.config.application.api.xml.DeploymentSpecXmlReader;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.CloudName;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.zone.RoutingMethod;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.security.KeyAlgorithm;
import com.yahoo.security.KeyUtils;
import com.yahoo.security.SignatureAlgorithm;
import com.yahoo.security.X509CertificateBuilder;
import com.yahoo.security.X509CertificateUtils;
import com.yahoo.test.ManualClock;
import com.yahoo.vespa.hosted.controller.ControllerTester;
import com.yahoo.vespa.hosted.controller.Instance;
import com.yahoo.vespa.hosted.controller.api.integration.certificates.EndpointCertificateMetadata;
import com.yahoo.vespa.hosted.controller.api.integration.certificates.EndpointCertificateMock;
import com.yahoo.vespa.hosted.controller.api.integration.certificates.EndpointCertificateValidatorImpl;
import com.yahoo.vespa.hosted.controller.application.pkg.ApplicationPackage;
import com.yahoo.vespa.hosted.controller.deployment.ApplicationPackageBuilder;
import com.yahoo.vespa.hosted.controller.integration.SecretStoreMock;
import com.yahoo.vespa.hosted.controller.integration.ZoneApiMock;
import com.yahoo.vespa.hosted.controller.persistence.CuratorDb;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.security.auth.x500.X500Principal;
import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author andreer
 */
public class EndpointCertificatesTest {

    private final ControllerTester tester = new ControllerTester();
    private final SecretStoreMock secretStore = new SecretStoreMock();
    private final CuratorDb mockCuratorDb = tester.curator();
    private final ManualClock clock = tester.clock();
    private final EndpointCertificateMock endpointCertificateMock = new EndpointCertificateMock(new ManualClock());
    private final EndpointCertificateValidatorImpl endpointCertificateValidator = new EndpointCertificateValidatorImpl(secretStore, clock);
    private final EndpointCertificates endpointCertificates = new EndpointCertificates(tester.controller(), endpointCertificateMock, endpointCertificateValidator);
    private final KeyPair testKeyPair = KeyUtils.generateKeypair(KeyAlgorithm.EC, 192);

    private X509Certificate testCertificate;
    private X509Certificate testCertificate2;

    private static final List<String> expectedSans = List.of(
            "vt2ktgkqme5zlnp4tj4ttyor7fj3v7q5o.vespa.oath.cloud",
            "default.default.global.vespa.oath.cloud",
            "*.default.default.global.vespa.oath.cloud",
            "default.default.aws-us-east-1a.vespa.oath.cloud",
            "*.default.default.aws-us-east-1a.vespa.oath.cloud",
            "default.default.us-east-1.test.vespa.oath.cloud",
            "*.default.default.us-east-1.test.vespa.oath.cloud",
            "default.default.us-east-3.staging.vespa.oath.cloud",
            "*.default.default.us-east-3.staging.vespa.oath.cloud"
    );

    private static final List<String> expectedAdditionalSans = List.of(
            "default.default.ap-northeast-1.vespa.oath.cloud",
            "*.default.default.ap-northeast-1.vespa.oath.cloud"
    );

    private static final List<String> expectedCombinedSans = new ArrayList<>() {{
        addAll(expectedSans);
        addAll(expectedAdditionalSans);
    }};

    private static final List<String> expectedDevSans = List.of(
            "vt2ktgkqme5zlnp4tj4ttyor7fj3v7q5o.vespa.oath.cloud",
            "default.default.us-east-1.dev.vespa.oath.cloud",
            "*.default.default.us-east-1.dev.vespa.oath.cloud"
    );

    private X509Certificate makeTestCert(List<String> sans) {
        X509CertificateBuilder x509CertificateBuilder = X509CertificateBuilder
                .fromKeypair(
                        testKeyPair,
                        new X500Principal("CN=test"),
                        clock.instant(), clock.instant().plus(5, ChronoUnit.MINUTES),
                        SignatureAlgorithm.SHA256_WITH_ECDSA,
                        X509CertificateBuilder.generateRandomSerialNumber());
        for (String san : sans) x509CertificateBuilder = x509CertificateBuilder.addSubjectAlternativeName(san);
        return x509CertificateBuilder.build();
    }

    private final Instance testInstance = new Instance(ApplicationId.defaultId());
    private final String testKeyName = "testKeyName";
    private final String testCertName = "testCertName";
    private ZoneId testZone;

    @BeforeEach
    public void setUp() {
        tester.zoneRegistry().exclusiveRoutingIn(tester.zoneRegistry().zones().all().zones());
        testZone = tester.zoneRegistry().zones().all().routingMethod(RoutingMethod.exclusive).in(Environment.prod).zones().stream().findFirst().orElseThrow().getId();
        clock.setInstant(Instant.EPOCH);
        testCertificate = makeTestCert(expectedSans);
        testCertificate2 = makeTestCert(expectedCombinedSans);
    }

    @Test
    void provisions_new_certificate_in_dev() {
        ZoneId testZone = tester.zoneRegistry().zones().all().routingMethod(RoutingMethod.exclusive).in(Environment.dev).zones().stream().findFirst().orElseThrow().getId();
        Optional<EndpointCertificateMetadata> endpointCertificateMetadata = endpointCertificates.getMetadata(testInstance, testZone, DeploymentSpec.empty);
        assertTrue(endpointCertificateMetadata.isPresent());
        assertTrue(endpointCertificateMetadata.get().keyName().matches("vespa.tls.default.default.*-key"));
        assertTrue(endpointCertificateMetadata.get().certName().matches("vespa.tls.default.default.*-cert"));
        assertEquals(0, endpointCertificateMetadata.get().version());
        assertEquals(expectedDevSans, endpointCertificateMetadata.get().requestedDnsSans());
    }

    @Test
    void provisions_new_certificate_in_prod() {
        Optional<EndpointCertificateMetadata> endpointCertificateMetadata = endpointCertificates.getMetadata(testInstance, testZone, DeploymentSpec.empty);
        assertTrue(endpointCertificateMetadata.isPresent());
        assertTrue(endpointCertificateMetadata.get().keyName().matches("vespa.tls.default.default.*-key"));
        assertTrue(endpointCertificateMetadata.get().certName().matches("vespa.tls.default.default.*-cert"));
        assertEquals(0, endpointCertificateMetadata.get().version());
        assertEquals(expectedSans, endpointCertificateMetadata.get().requestedDnsSans());
    }

    private ControllerTester publicTester() {
        ControllerTester publicTester = new ControllerTester(SystemName.Public);
        publicTester.zoneRegistry().setZones(tester.zoneRegistry().zones().all().zones());
        return publicTester;
    }

    @Test
    void provisions_new_certificate_in_public_prod() {
        ControllerTester tester = publicTester();
        EndpointCertificateValidatorImpl endpointCertificateValidator = new EndpointCertificateValidatorImpl(secretStore, clock);
        EndpointCertificates endpointCertificates = new EndpointCertificates(tester.controller(), endpointCertificateMock, endpointCertificateValidator);
        List<String> expectedSans = List.of(
                "vt2ktgkqme5zlnp4tj4ttyor7fj3v7q5o.internal.vespa-app.cloud",
                "default.default.g.vespa-app.cloud",
                "*.default.default.g.vespa-app.cloud",
                "default.default.aws-us-east-1a.z.vespa-app.cloud",
                "*.default.default.aws-us-east-1a.z.vespa-app.cloud",
                "default.default.us-east-1.test.z.vespa-app.cloud",
                "*.default.default.us-east-1.test.z.vespa-app.cloud",
                "default.default.us-east-3.staging.z.vespa-app.cloud",
                "*.default.default.us-east-3.staging.z.vespa-app.cloud"
        );
        Optional<EndpointCertificateMetadata> endpointCertificateMetadata = endpointCertificates.getMetadata(testInstance, testZone, DeploymentSpec.empty);
        assertTrue(endpointCertificateMetadata.isPresent());
        assertTrue(endpointCertificateMetadata.get().keyName().matches("vespa.tls.default.default.*-key"));
        assertTrue(endpointCertificateMetadata.get().certName().matches("vespa.tls.default.default.*-cert"));
        assertEquals(0, endpointCertificateMetadata.get().version());
        assertEquals(expectedSans, endpointCertificateMetadata.get().requestedDnsSans());
    }

    @Test
    void reuses_stored_certificate_metadata() {
        mockCuratorDb.writeEndpointCertificateMetadata(testInstance.id(), new EndpointCertificateMetadata(testKeyName, testCertName, 7, 0, "request_id", Optional.of("leaf-request-uuid"),
                List.of("vt2ktgkqme5zlnp4tj4ttyor7fj3v7q5o.vespa.oath.cloud",
                        "default.default.global.vespa.oath.cloud",
                        "*.default.default.global.vespa.oath.cloud",
                        "default.default.aws-us-east-1a.vespa.oath.cloud",
                        "*.default.default.aws-us-east-1a.vespa.oath.cloud"),
                "", Optional.empty(), Optional.empty()));
        secretStore.setSecret(testKeyName, KeyUtils.toPem(testKeyPair.getPrivate()), 7);
        secretStore.setSecret(testCertName, X509CertificateUtils.toPem(testCertificate) + X509CertificateUtils.toPem(testCertificate), 7);
        Optional<EndpointCertificateMetadata> endpointCertificateMetadata = endpointCertificates.getMetadata(testInstance, testZone, DeploymentSpec.empty);
        assertTrue(endpointCertificateMetadata.isPresent());
        assertEquals(testKeyName, endpointCertificateMetadata.get().keyName());
        assertEquals(testCertName, endpointCertificateMetadata.get().certName());
        assertEquals(7, endpointCertificateMetadata.get().version());
    }

    @Test
    void reprovisions_certificate_when_necessary() {
        mockCuratorDb.writeEndpointCertificateMetadata(testInstance.id(), new EndpointCertificateMetadata(testKeyName, testCertName, -1, 0, "root-request-uuid", Optional.of("leaf-request-uuid"), List.of(), "issuer", Optional.empty(), Optional.empty()));
        secretStore.setSecret("vespa.tls.default.default.default-key", KeyUtils.toPem(testKeyPair.getPrivate()), 0);
        secretStore.setSecret("vespa.tls.default.default.default-cert", X509CertificateUtils.toPem(testCertificate) + X509CertificateUtils.toPem(testCertificate), 0);
        Optional<EndpointCertificateMetadata> endpointCertificateMetadata = endpointCertificates.getMetadata(testInstance, testZone, DeploymentSpec.empty);
        assertTrue(endpointCertificateMetadata.isPresent());
        assertEquals(0, endpointCertificateMetadata.get().version());
        assertEquals(endpointCertificateMetadata, mockCuratorDb.readEndpointCertificateMetadata(testInstance.id()));
    }

    @Test
    void reprovisions_certificate_with_added_sans_when_deploying_to_new_zone() {
        ZoneId testZone = ZoneId.from("prod.ap-northeast-1");

        mockCuratorDb.writeEndpointCertificateMetadata(testInstance.id(), new EndpointCertificateMetadata(testKeyName, testCertName, -1, 0, "original-request-uuid", Optional.of("leaf-request-uuid"), expectedSans, "mockCa", Optional.empty(), Optional.empty()));
        secretStore.setSecret("vespa.tls.default.default.default-key", KeyUtils.toPem(testKeyPair.getPrivate()), -1);
        secretStore.setSecret("vespa.tls.default.default.default-cert", X509CertificateUtils.toPem(testCertificate) + X509CertificateUtils.toPem(testCertificate), -1);

        secretStore.setSecret("vespa.tls.default.default.default-key", KeyUtils.toPem(testKeyPair.getPrivate()), 0);
        secretStore.setSecret("vespa.tls.default.default.default-cert", X509CertificateUtils.toPem(testCertificate2) + X509CertificateUtils.toPem(testCertificate2), 0);

        Optional<EndpointCertificateMetadata> endpointCertificateMetadata = endpointCertificates.getMetadata(testInstance, testZone, DeploymentSpec.empty);
        assertTrue(endpointCertificateMetadata.isPresent());
        assertEquals(0, endpointCertificateMetadata.get().version());
        assertEquals(endpointCertificateMetadata, mockCuratorDb.readEndpointCertificateMetadata(testInstance.id()));
        assertEquals("original-request-uuid", endpointCertificateMetadata.get().rootRequestId());
        assertNotEquals(Optional.of("leaf-request-uuid"), endpointCertificateMetadata.get().leafRequestId());
        assertEquals(Set.copyOf(expectedCombinedSans), Set.copyOf(endpointCertificateMetadata.get().requestedDnsSans()));
    }

    @Test
    void includes_zones_in_deployment_spec_when_deploying_to_staging() {
        DeploymentSpec deploymentSpec = new DeploymentSpecXmlReader(true).read(
                """
                        <deployment version="1.0">
                          <instance id="default">
                            <prod>
                              <region active="true">aws-us-east-1a</region>
                              <region active="true">ap-northeast-1</region>
                            </prod>
                          </instance>
                        </deployment>
                        """
        );

        ZoneId testZone = tester.zoneRegistry().zones().all().in(Environment.staging).zones().stream().findFirst().orElseThrow().getId();
        Optional<EndpointCertificateMetadata> endpointCertificateMetadata = endpointCertificates.getMetadata(testInstance, testZone, deploymentSpec);
        assertTrue(endpointCertificateMetadata.isPresent());
        assertTrue(endpointCertificateMetadata.get().keyName().matches("vespa.tls.default.default.*-key"));
        assertTrue(endpointCertificateMetadata.get().certName().matches("vespa.tls.default.default.*-cert"));
        assertEquals(0, endpointCertificateMetadata.get().version());
        assertEquals(Set.copyOf(expectedCombinedSans), Set.copyOf(endpointCertificateMetadata.get().requestedDnsSans()));
    }

    @Test
    void includes_application_endpoint_when_declared() {
        Instance instance = new Instance(ApplicationId.from("t1", "a1", "default"));
        ZoneId zone1 = ZoneId.from(Environment.prod, RegionName.from("aws-us-east-1c"));
        ZoneId zone2 = ZoneId.from(Environment.prod, RegionName.from("aws-us-west-2a"));
        ControllerTester tester = publicTester();
        tester.zoneRegistry().addZones(ZoneApiMock.newBuilder().with(CloudName.DEFAULT).with(zone1).build(),
                                       ZoneApiMock.newBuilder().with(CloudName.AWS).with(zone2).build());
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .instances("beta,main")
                .region(zone1.region())
                .region(zone2.region())
                .applicationEndpoint("a", "qrs", zone2.region().value(),
                                     Map.of(InstanceName.from("beta"), 2))
                .applicationEndpoint("b", "qrs", zone2.region().value(),
                                     Map.of(InstanceName.from("beta"), 1))
                .applicationEndpoint("c", "qrs",
                                     Map.of(zone1.region().value(), Map.of(InstanceName.from("beta"), 4,
                                                                           InstanceName.from("main"), 6),
                                            zone2.region().value(), Map.of(InstanceName.from("main"), 2)))
                .build();
        EndpointCertificateValidatorImpl endpointCertificateValidator = new EndpointCertificateValidatorImpl(secretStore, clock);
        EndpointCertificates endpointCertificates = new EndpointCertificates(tester.controller(), endpointCertificateMock, endpointCertificateValidator);
        List<String> expectedSans = Stream.of(
                "vlfms2wpoa4nyrka2s5lktucypjtxkqhv.internal.vespa-app.cloud",
                "a1.t1.g.vespa-app.cloud",
                "*.a1.t1.g.vespa-app.cloud",
                "a1.t1.a.vespa-app.cloud",
                "a1.t1.aws-us-west-2a.r.vespa-app.cloud",
                "a1.t1.aws-us-east-1c.r.vespa-app.cloud",
                "*.a1.t1.a.vespa-app.cloud",
                "*.a1.t1.aws-us-west-2a.r.vespa-app.cloud",
                "*.a1.t1.aws-us-east-1c.r.vespa-app.cloud",
                "a1.t1.aws-us-east-1c.z.vespa-app.cloud",
                "*.a1.t1.aws-us-east-1c.z.vespa-app.cloud",
                "a1.t1.us-east-1.test.z.vespa-app.cloud",
                "*.a1.t1.us-east-1.test.z.vespa-app.cloud",
                "a1.t1.us-east-3.staging.z.vespa-app.cloud",
                "*.a1.t1.us-east-3.staging.z.vespa-app.cloud"
        ).sorted().toList();
        Optional<EndpointCertificateMetadata> endpointCertificateMetadata = endpointCertificates.getMetadata(instance, zone1, applicationPackage.deploymentSpec());
        assertTrue(endpointCertificateMetadata.isPresent());
        assertTrue(endpointCertificateMetadata.get().keyName().matches("vespa.tls.t1.a1.*-key"));
        assertTrue(endpointCertificateMetadata.get().certName().matches("vespa.tls.t1.a1.*-cert"));
        assertEquals(0, endpointCertificateMetadata.get().version());
        assertEquals(expectedSans, endpointCertificateMetadata.get().requestedDnsSans().stream().sorted().toList());
    }

}
