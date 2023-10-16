// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
import com.yahoo.transaction.Mutex;
import com.yahoo.vespa.flags.Flags;
import com.yahoo.vespa.hosted.controller.ControllerTester;
import com.yahoo.vespa.hosted.controller.api.identifiers.DeploymentId;
import com.yahoo.vespa.hosted.controller.api.integration.certificates.EndpointCertificate;
import com.yahoo.vespa.hosted.controller.api.integration.certificates.EndpointCertificateProviderMock;
import com.yahoo.vespa.hosted.controller.api.integration.certificates.EndpointCertificateValidatorImpl;
import com.yahoo.vespa.hosted.controller.api.integration.certificates.EndpointCertificateValidatorMock;
import com.yahoo.vespa.hosted.controller.application.TenantAndApplicationId;
import com.yahoo.vespa.hosted.controller.application.pkg.ApplicationPackage;
import com.yahoo.vespa.hosted.controller.deployment.ApplicationPackageBuilder;
import com.yahoo.vespa.hosted.controller.integration.SecretStoreMock;
import com.yahoo.vespa.hosted.controller.integration.ZoneApiMock;
import com.yahoo.vespa.hosted.controller.persistence.CuratorDb;
import com.yahoo.vespa.hosted.controller.routing.EndpointConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.security.auth.x500.X500Principal;
import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author andreer
 */
public class EndpointCertificatesTest {

    private final ControllerTester tester = new ControllerTester();
    private final SecretStoreMock secretStore = new SecretStoreMock();
    private final CuratorDb curator = tester.curator();
    private final ManualClock clock = tester.clock();
    private final EndpointCertificateProviderMock endpointCertificateProviderMock = new EndpointCertificateProviderMock();
    private final EndpointCertificateValidatorImpl endpointCertificateValidator = new EndpointCertificateValidatorImpl(secretStore, clock);
    private final EndpointCertificates endpointCertificates = new EndpointCertificates(tester.controller(), endpointCertificateProviderMock, endpointCertificateValidator);
    private final KeyPair testKeyPair = KeyUtils.generateKeypair(KeyAlgorithm.EC, 192);
    private final Mutex lock = () -> {};

    private X509Certificate testCertificate;
    private X509Certificate testCertificate2;

    private static final List<String> expectedSans = List.of(
            "vt2ktgkqme5zlnp4tj4ttyor7fj3v7q5o.vespa.oath.cloud",
            "default.default.global.vespa.oath.cloud",
            "*.default.default.global.vespa.oath.cloud",
            "default.default.aws-us-east-1a.vespa.oath.cloud",
            "*.default.default.aws-us-east-1a.vespa.oath.cloud",
            "*.f5549014.z.vespa.oath.cloud",
            "*.f5549014.g.vespa.oath.cloud",
            "*.f5549014.a.vespa.oath.cloud",
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
            "*.default.default.us-east-1.dev.vespa.oath.cloud",
            "*.f5549014.z.vespa.oath.cloud",
            "*.f5549014.g.vespa.oath.cloud",
            "*.f5549014.a.vespa.oath.cloud"
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

    private final ApplicationId instance = ApplicationId.defaultId();
    private final String testKeyName = "testKeyName";
    private final String testCertName = "testCertName";
    private ZoneId prodZone;

    @BeforeEach
    public void setUp() {
        tester.zoneRegistry().exclusiveRoutingIn(tester.zoneRegistry().zones().all().zones());
        prodZone = tester.zoneRegistry().zones().all().routingMethod(RoutingMethod.exclusive).in(Environment.prod).zones().stream().findFirst().orElseThrow().getId();
        clock.setInstant(Instant.EPOCH);
        testCertificate = makeTestCert(expectedSans);
        testCertificate2 = makeTestCert(expectedCombinedSans);
    }

    @Test
    void provisions_new_certificate_in_dev() {
        ZoneId testZone = tester.zoneRegistry().zones().all().routingMethod(RoutingMethod.exclusive).in(Environment.dev).zones().stream().findFirst().orElseThrow().getId();
        EndpointCertificate cert = endpointCertificates.get(new DeploymentId(instance, testZone), DeploymentSpec.empty, lock);
        assertTrue(cert.keyName().matches("vespa.tls.default.default.*-key"));
        assertTrue(cert.certName().matches("vespa.tls.default.default.*-cert"));
        assertEquals(0, cert.version());
        assertEquals(expectedDevSans, cert.requestedDnsSans());
    }

    @Test
    void provisions_new_certificate_in_prod() {
        EndpointCertificate cert = endpointCertificates.get(new DeploymentId(instance, prodZone), DeploymentSpec.empty, lock);
        assertTrue(cert.keyName().matches("vespa.tls.default.default.*-key"));
        assertTrue(cert.certName().matches("vespa.tls.default.default.*-cert"));
        assertEquals(0, cert.version());
        assertEquals(expectedSans, cert.requestedDnsSans());
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
        EndpointCertificates endpointCertificates = new EndpointCertificates(tester.controller(), endpointCertificateProviderMock, endpointCertificateValidator);
        List<String> expectedSans = List.of(
                "vt2ktgkqme5zlnp4tj4ttyor7fj3v7q5o.internal.vespa-app.cloud",
                "default.default.g.vespa-app.cloud",
                "*.default.default.g.vespa-app.cloud",
                "default.default.aws-us-east-1a.z.vespa-app.cloud",
                "*.default.default.aws-us-east-1a.z.vespa-app.cloud",
                "*.f5549014.z.vespa-app.cloud",
                "*.f5549014.g.vespa-app.cloud",
                "*.f5549014.a.vespa-app.cloud",
                "default.default.us-east-1.test.z.vespa-app.cloud",
                "*.default.default.us-east-1.test.z.vespa-app.cloud",
                "default.default.us-east-3.staging.z.vespa-app.cloud",
                "*.default.default.us-east-3.staging.z.vespa-app.cloud"
        );
        EndpointCertificate cert = endpointCertificates.get(new DeploymentId(instance, prodZone), DeploymentSpec.empty, lock);
        assertTrue(cert.keyName().matches("vespa.tls.default.default.*-key"));
        assertTrue(cert.certName().matches("vespa.tls.default.default.*-cert"));
        assertEquals(0, cert.version());
        assertEquals(expectedSans, cert.requestedDnsSans());
    }

    @Test
    void reuses_stored_certificate() {
        curator.writeAssignedCertificate(assignedCertificate(instance, new EndpointCertificate(testKeyName, testCertName, 7, 0, "request_id", Optional.of("leaf-request-uuid"),
                                                                                               List.of("vt2ktgkqme5zlnp4tj4ttyor7fj3v7q5o.vespa.oath.cloud",
                        "default.default.global.vespa.oath.cloud",
                        "*.default.default.global.vespa.oath.cloud",
                        "default.default.aws-us-east-1a.vespa.oath.cloud",
                        "*.default.default.aws-us-east-1a.vespa.oath.cloud",
                        "*.f5549014.z.vespa.oath.cloud",
                        "*.f5549014.g.vespa.oath.cloud",
                        "*.f5549014.a.vespa.oath.cloud"),
                                                                                               "", Optional.empty(), Optional.empty(), Optional.empty())));
        secretStore.setSecret(testKeyName, KeyUtils.toPem(testKeyPair.getPrivate()), 7);
        secretStore.setSecret(testCertName, X509CertificateUtils.toPem(testCertificate) + X509CertificateUtils.toPem(testCertificate), 7);
        EndpointCertificate cert = endpointCertificates.get(new DeploymentId(instance, prodZone), DeploymentSpec.empty, lock);
        assertEquals(testKeyName, cert.keyName());
        assertEquals(testCertName, cert.certName());
        assertEquals(7, cert.version());
    }

    @Test
    void reprovisions_certificate_when_necessary() {
        curator.writeAssignedCertificate(assignedCertificate(instance, new EndpointCertificate(testKeyName, testCertName, -1, 0, "root-request-uuid", Optional.of("leaf-request-uuid"), List.of(), "issuer", Optional.empty(), Optional.empty(), Optional.empty())));
        secretStore.setSecret("vespa.tls.default.default.default-key", KeyUtils.toPem(testKeyPair.getPrivate()), 0);
        secretStore.setSecret("vespa.tls.default.default.default-cert", X509CertificateUtils.toPem(testCertificate) + X509CertificateUtils.toPem(testCertificate), 0);
        EndpointCertificate cert = endpointCertificates.get(new DeploymentId(instance, prodZone), DeploymentSpec.empty, lock);
        assertEquals(0, cert.version());
        assertEquals(cert, curator.readAssignedCertificate(instance).map(AssignedCertificate::certificate).get());
    }

    @Test
    void reprovisions_certificate_with_added_sans_when_deploying_to_new_zone() {
        ZoneId testZone = ZoneId.from("prod.ap-northeast-1");

        curator.writeAssignedCertificate(assignedCertificate(instance, new EndpointCertificate(testKeyName, testCertName, -1, 0, "original-request-uuid", Optional.of("leaf-request-uuid"), expectedSans, "mockCa", Optional.empty(), Optional.empty(), Optional.empty())));
        secretStore.setSecret("vespa.tls.default.default.default-key", KeyUtils.toPem(testKeyPair.getPrivate()), -1);
        secretStore.setSecret("vespa.tls.default.default.default-cert", X509CertificateUtils.toPem(testCertificate) + X509CertificateUtils.toPem(testCertificate), -1);

        secretStore.setSecret("vespa.tls.default.default.default-key", KeyUtils.toPem(testKeyPair.getPrivate()), 0);
        secretStore.setSecret("vespa.tls.default.default.default-cert", X509CertificateUtils.toPem(testCertificate2) + X509CertificateUtils.toPem(testCertificate2), 0);

        EndpointCertificate cert = endpointCertificates.get(new DeploymentId(instance, testZone), DeploymentSpec.empty, lock);
        assertEquals(0, cert.version());
        assertEquals(cert, curator.readAssignedCertificate(instance).map(AssignedCertificate::certificate).get());
        assertEquals("original-request-uuid", cert.rootRequestId());
        assertNotEquals(Optional.of("leaf-request-uuid"), cert.leafRequestId());
        assertEquals(Set.copyOf(expectedCombinedSans), Set.copyOf(cert.requestedDnsSans()));
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
        EndpointCertificate cert = endpointCertificates.get(new DeploymentId(instance, testZone), deploymentSpec, lock);
        assertTrue(cert.keyName().matches("vespa.tls.default.default.*-key"));
        assertTrue(cert.certName().matches("vespa.tls.default.default.*-cert"));
        assertEquals(0, cert.version());
        assertEquals(Set.copyOf(expectedCombinedSans), Set.copyOf(cert.requestedDnsSans()));
    }

    @Test
    void includes_application_endpoint_when_declared() {
        ApplicationId instance = ApplicationId.from("t1", "a1", "default");
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
        EndpointCertificates endpointCertificates = new EndpointCertificates(tester.controller(), endpointCertificateProviderMock, endpointCertificateValidator);
        List<String> expectedSans = Stream.of(
                "vlfms2wpoa4nyrka2s5lktucypjtxkqhv.internal.vespa-app.cloud",
                "a1.t1.g.vespa-app.cloud",
                "*.a1.t1.g.vespa-app.cloud",
                "a1.t1.a.vespa-app.cloud",
                "*.a1.t1.a.vespa-app.cloud",
                "a1.t1.aws-us-east-1c.z.vespa-app.cloud",
                "*.a1.t1.aws-us-east-1c.z.vespa-app.cloud",
                "a1.t1.us-east-1.test.z.vespa-app.cloud",
                "*.a1.t1.us-east-1.test.z.vespa-app.cloud",
                "a1.t1.us-east-3.staging.z.vespa-app.cloud",
                "*.a1.t1.us-east-3.staging.z.vespa-app.cloud",
                "*.f5549014.z.vespa-app.cloud",
                "*.f5549014.g.vespa-app.cloud",
                "*.f5549014.a.vespa-app.cloud"
        ).sorted().toList();
        EndpointCertificate cert = endpointCertificates.get(new DeploymentId(instance, zone1), applicationPackage.deploymentSpec(), lock);
        assertTrue(cert.keyName().matches("vespa.tls.t1.a1.*-key"));
        assertTrue(cert.certName().matches("vespa.tls.t1.a1.*-cert"));
        assertEquals(0, cert.version());
        assertEquals(expectedSans, cert.requestedDnsSans().stream().sorted().toList());
    }

    @Test
    public void assign_certificate_from_pool() {
        setEndpointConfig(tester, EndpointConfig.generated);
        try {
            addCertificateToPool("bad0f00d", UnassignedCertificate.State.requested, tester);
            endpointCertificates.get(new DeploymentId(instance, prodZone), DeploymentSpec.empty, lock);
            fail("Expected exception as certificate is not ready");
        } catch (IllegalArgumentException ignored) {}

        // Advance clock to verify last requested time
        clock.advance(Duration.ofDays(3));

        // Certificate is assigned from pool instead. The previously assigned certificate will eventually be cleaned up
        // by EndpointCertificateMaintainer
        { // prod
            String certId = "bad0f00d";
            addCertificateToPool(certId, UnassignedCertificate.State.ready, tester);
            EndpointCertificate cert = endpointCertificates.get(new DeploymentId(instance, prodZone), DeploymentSpec.empty, lock);
            assertEquals(certId, cert.generatedId().get());
            assertEquals(certId, tester.curator().readAssignedCertificate(TenantAndApplicationId.from(instance), Optional.empty()).get().certificate().generatedId().get(), "Certificate is assigned at application-level");
            assertTrue(tester.controller().curator().readUnassignedCertificate(certId).isEmpty(), "Certificate is removed from pool");
            assertEquals(clock.instant().getEpochSecond(), cert.lastRequested());
        }

        { // dev
            String certId = "f00d0bad";
            addCertificateToPool(certId, UnassignedCertificate.State.ready, tester);
            ZoneId devZone = tester.zoneRegistry().zones().all().routingMethod(RoutingMethod.exclusive).in(Environment.dev).zones().stream().findFirst().orElseThrow().getId();
            EndpointCertificate cert = endpointCertificates.get(new DeploymentId(instance, devZone), DeploymentSpec.empty, lock);
            assertEquals(certId, cert.generatedId().get());
            assertEquals(certId, tester.curator().readAssignedCertificate(instance).get().certificate().generatedId().get(), "Certificate is assigned at instance-level");
            assertTrue(tester.controller().curator().readUnassignedCertificate(certId).isEmpty(), "Certificate is removed from pool");
            assertEquals(clock.instant().getEpochSecond(), cert.lastRequested());
        }
    }

    @Test
    public void certificate_migration() {
        // An application is initially deployed with legacy config (the default)
        ZoneId zone1 = ZoneId.from(Environment.prod, RegionName.from("aws-us-east-1c"));
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder().region(zone1.region())
                                                                               .build();
        ControllerTester tester = publicTester();
        EndpointCertificates endpointCertificates = new EndpointCertificates(tester.controller(), endpointCertificateProviderMock, new EndpointCertificateValidatorMock());
        ApplicationId instance = ApplicationId.from("t1", "a1", "default");
        DeploymentId deployment0 = new DeploymentId(instance, zone1);
        final EndpointCertificate certificate = endpointCertificates.get(deployment0, applicationPackage.deploymentSpec(), lock);
        final String generatedId = certificate.generatedId().get();
        assertEquals(List.of("vlfms2wpoa4nyrka2s5lktucypjtxkqhv.internal.vespa-app.cloud",
                             "a1.t1.g.vespa-app.cloud",
                             "*.a1.t1.g.vespa-app.cloud",
                             "a1.t1.aws-us-east-1c.z.vespa-app.cloud",
                             "*.a1.t1.aws-us-east-1c.z.vespa-app.cloud",
                             "*.f5549014.z.vespa-app.cloud",
                             "*.f5549014.g.vespa-app.cloud",
                             "*.f5549014.a.vespa-app.cloud",
                             "a1.t1.us-east-1.test.z.vespa-app.cloud",
                             "*.a1.t1.us-east-1.test.z.vespa-app.cloud",
                             "a1.t1.us-east-3.staging.z.vespa-app.cloud",
                             "*.a1.t1.us-east-3.staging.z.vespa-app.cloud"),
                     certificate.requestedDnsSans());
        Optional<AssignedCertificate> assignedCertificate = tester.curator().readAssignedCertificate(deployment0.applicationId());
        assertTrue(assignedCertificate.isPresent(), "Certificate is assigned at instance level");
        assertTrue(assignedCertificate.get().certificate().generatedId().isPresent(), "Certificate contains generated ID");

        // Re-requesting certificate does not make any changes, except last requested time
        tester.clock().advance(Duration.ofHours(1));
        assertEquals(certificate.withLastRequested(tester.clock().instant().getEpochSecond()),
                     endpointCertificates.get(deployment0, applicationPackage.deploymentSpec(), lock),
                     "Next request returns same certificate and updates last requested time");

        // An additional instance is added to deployment spec
        applicationPackage = new ApplicationPackageBuilder().instances("default,beta")
                                                            .region(zone1.region())
                                                            .build();
        DeploymentId deployment1 = new DeploymentId(ApplicationId.from("t1", "a1", "beta"), zone1);
        EndpointCertificate betaCert = endpointCertificates.get(deployment1, applicationPackage.deploymentSpec(), lock);
        assertEquals(List.of("v43ctkgqim52zsbwefrg6ixkuwidvsumy.internal.vespa-app.cloud",
                             "beta.a1.t1.g.vespa-app.cloud",
                             "*.beta.a1.t1.g.vespa-app.cloud",
                             "beta.a1.t1.aws-us-east-1c.z.vespa-app.cloud",
                             "*.beta.a1.t1.aws-us-east-1c.z.vespa-app.cloud",
                             "*.f5549014.z.vespa-app.cloud",
                             "*.f5549014.g.vespa-app.cloud",
                             "*.f5549014.a.vespa-app.cloud",
                             "beta.a1.t1.us-east-1.test.z.vespa-app.cloud",
                             "*.beta.a1.t1.us-east-1.test.z.vespa-app.cloud",
                             "beta.a1.t1.us-east-3.staging.z.vespa-app.cloud",
                             "*.beta.a1.t1.us-east-3.staging.z.vespa-app.cloud"),
                     betaCert.requestedDnsSans());
        assertEquals(generatedId, betaCert.generatedId().get(), "Certificate inherits generated ID of existing instance");

        // A dev instance is deployed
        DeploymentId devDeployment0 = new DeploymentId(ApplicationId.from("t1", "a1", "dev"),
                                                   ZoneId.from("dev", "us-east-1"));
        EndpointCertificate devCert0 = endpointCertificates.get(devDeployment0, applicationPackage.deploymentSpec(), lock);
        assertNotEquals(generatedId, devCert0.generatedId().get(), "Dev deployments gets a new generated ID");
        assertEquals(List.of("vld3y4mggzpd5wmm5jmldzcbyetjoqtzq.internal.vespa-app.cloud",
                             "dev.a1.t1.us-east-1.dev.z.vespa-app.cloud",
                             "*.dev.a1.t1.us-east-1.dev.z.vespa-app.cloud",
                             "*.a89ff7c6.z.vespa-app.cloud",
                             "*.a89ff7c6.g.vespa-app.cloud",
                             "*.a89ff7c6.a.vespa-app.cloud"),
                     devCert0.requestedDnsSans());

        // Application switches to combined config
        setEndpointConfig(tester, EndpointConfig.combined);
        tester.clock().advance(Duration.ofHours(1));
        assertEquals(certificate.withLastRequested(tester.clock().instant().getEpochSecond()),
                     endpointCertificates.get(deployment0, applicationPackage.deploymentSpec(), lock),
                     "No change to certificate: Existing certificate is compatible with " +
                     EndpointConfig.combined + " config");
        assertTrue(tester.curator().readAssignedCertificate(deployment0.applicationId()).isPresent(), "Certificate is assigned at instance level");
        assertFalse(tester.curator().readAssignedCertificate(TenantAndApplicationId.from(deployment0.applicationId()), Optional.empty()).isPresent(),
                   "Certificate is not assigned at application level");

        // Application switches to generated config
        setEndpointConfig(tester, EndpointConfig.generated);
        tester.clock().advance(Duration.ofHours(1));
        assertEquals(certificate.withLastRequested(tester.clock().instant().getEpochSecond()),
                     endpointCertificates.get(deployment0, applicationPackage.deploymentSpec(), lock),
                     "No change to certificate: Existing certificate is compatible with " +
                     EndpointConfig.generated + " config");
        assertFalse(tester.curator().readAssignedCertificate(deployment0.applicationId()).isPresent(), "Certificate is no longer assigned at instance level");
        assertTrue(tester.curator().readAssignedCertificate(TenantAndApplicationId.from(deployment0.applicationId()), Optional.empty()).isPresent(),
                   "Certificate is assigned at application level");

        // Both instances still use the same certificate
        assertEquals(endpointCertificates.get(deployment0, applicationPackage.deploymentSpec(), lock),
                     endpointCertificates.get(deployment1, applicationPackage.deploymentSpec(), lock));

        // Another dev instance is deployed, and is assigned certificate from pool
        String poolCertId0 = "badf00d0";
        addCertificateToPool(poolCertId0, UnassignedCertificate.State.ready, tester);
        EndpointCertificate devCert1 = endpointCertificates.get(new DeploymentId(ApplicationId.from("t1", "a1", "dev2"),
                                                                                        ZoneId.from("dev", "us-east-1")),
                                                                       applicationPackage.deploymentSpec(), lock);
        assertEquals(poolCertId0, devCert1.generatedId().get());

        // Another application is deployed, and is assigned certificate from pool
        String poolCertId1 = "badf00d1";
        addCertificateToPool(poolCertId1, UnassignedCertificate.State.ready, tester);
        EndpointCertificate prodCertificate = endpointCertificates.get(new DeploymentId(ApplicationId.from("t1", "a2", "default"),
                                                                                        ZoneId.from("prod", "us-east-1")),
                                                                       applicationPackage.deploymentSpec(), lock);
        assertEquals(poolCertId1, prodCertificate.generatedId().get());

        // Application switches back to legacy config
        setEndpointConfig(tester, EndpointConfig.legacy);
        EndpointCertificate reissuedCertificate = endpointCertificates.get(deployment0, applicationPackage.deploymentSpec(), lock);
        assertEquals(certificate.requestedDnsSans(), reissuedCertificate.requestedDnsSans());
        assertTrue(tester.curator().readAssignedCertificate(deployment0.applicationId()).isPresent(), "Certificate is assigned at instance level again");
        assertTrue(tester.curator().readAssignedCertificate(TenantAndApplicationId.from(deployment0.applicationId()), Optional.empty()).isPresent(),
                   "Certificate is still assigned at application level"); // Not removed because the assumption is that the application will eventually migrate back
    }

    private void setEndpointConfig(ControllerTester tester, EndpointConfig config) {
        tester.flagSource().withStringFlag(Flags.ENDPOINT_CONFIG.id(), config.name());
    }

    private void addCertificateToPool(String id, UnassignedCertificate.State state, ControllerTester tester) {
        EndpointCertificate cert = new EndpointCertificate(testKeyName,
                                                           testCertName,
                                                           1,
                                                           0,
                                                           "request-id",
                                                           Optional.of("leaf-request-uuid"),
                                                           List.of("*." + id + ".z.vespa.oath.cloud",
                                                                   "*." + id + ".g.vespa.oath.cloud",
                                                                   "*." + id + ".a.vespa.oath.cloud"),
                                                           "",
                                                           Optional.empty(),
                                                           Optional.empty(),
                                                           Optional.of(id));
        UnassignedCertificate pooledCert = new UnassignedCertificate(cert, state);
        tester.controller().curator().writeUnassignedCertificate(pooledCert);
    }

    private static AssignedCertificate assignedCertificate(ApplicationId instance, EndpointCertificate certificate) {
        return new AssignedCertificate(TenantAndApplicationId.from(instance), Optional.of(instance.instance()), certificate, false);
    }

}
