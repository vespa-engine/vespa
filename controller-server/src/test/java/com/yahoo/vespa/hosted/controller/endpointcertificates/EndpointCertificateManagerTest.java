package com.yahoo.vespa.hosted.controller.endpointcertificates;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.security.KeyAlgorithm;
import com.yahoo.security.KeyUtils;
import com.yahoo.security.SignatureAlgorithm;
import com.yahoo.security.X509CertificateBuilder;
import com.yahoo.security.X509CertificateUtils;
import com.yahoo.vespa.flags.Flags;
import com.yahoo.vespa.flags.InMemoryFlagSource;
import com.yahoo.vespa.hosted.controller.Instance;
import com.yahoo.vespa.hosted.controller.api.integration.certificates.ApplicationCertificateMock;
import com.yahoo.vespa.hosted.controller.api.integration.certificates.EndpointCertificateMetadata;
import com.yahoo.vespa.hosted.controller.integration.SecretStoreMock;
import com.yahoo.vespa.hosted.controller.integration.ZoneRegistryMock;
import com.yahoo.vespa.hosted.controller.persistence.MockCuratorDb;
import org.junit.Before;
import org.junit.Test;

import javax.security.auth.x500.X500Principal;
import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author andreer
 */
public class EndpointCertificateManagerTest {

    private final SecretStoreMock secretStore = new SecretStoreMock();
    private final ZoneRegistryMock zoneRegistryMock = new ZoneRegistryMock(SystemName.main);
    private final MockCuratorDb mockCuratorDb = new MockCuratorDb();
    private final ApplicationCertificateMock applicationCertificateMock = new ApplicationCertificateMock();
    private final InMemoryFlagSource inMemoryFlagSource = new InMemoryFlagSource();
    private final Clock clock = Clock.systemUTC();
    private final EndpointCertificateManager endpointCertificateManager = new EndpointCertificateManager(zoneRegistryMock, mockCuratorDb, secretStore, applicationCertificateMock, clock, inMemoryFlagSource);

    private static final KeyPair testKeyPair = KeyUtils.generateKeypair(KeyAlgorithm.EC, 192);
    private static final X509Certificate testCertificate = X509CertificateBuilder
            .fromKeypair(
                    testKeyPair,
                    new X500Principal("CN=test"),
                    Instant.now(), Instant.now().plus(5, ChronoUnit.MINUTES),
                    SignatureAlgorithm.SHA256_WITH_ECDSA,
                    X509CertificateBuilder.generateRandomSerialNumber())
            .addSubjectAlternativeName("vt2ktgkqme5zlnp4tj4ttyor7fj3v7q5o.vespa.oath.cloud")
            .addSubjectAlternativeName("default.default.global.vespa.oath.cloud")
            .addSubjectAlternativeName("*.default.default.global.vespa.oath.cloud")
            .addSubjectAlternativeName("default.default.us-east-1.test.vespa.oath.cloud")
            .addSubjectAlternativeName("*.default.default.us-east-1.test.vespa.oath.cloud")
            .build();

    private final Instance testInstance = new Instance(ApplicationId.defaultId());
    private final String testKeyName = "testKeyName";
    private final String testCertName = "testCertName";
    private ZoneId testZone;

    @Before
    public void setUp() {
        zoneRegistryMock.exclusiveRoutingIn(zoneRegistryMock.zones().all().zones());
        testZone = zoneRegistryMock.zones().directlyRouted().zones().stream().findFirst().get().getId();
    }

    @Test
    public void provisions_new_certificate() {
        Optional<EndpointCertificateMetadata> endpointCertificateMetadata = endpointCertificateManager.getEndpointCertificateMetadata(testInstance, testZone);
        assertTrue(endpointCertificateMetadata.isPresent());
        assertTrue(endpointCertificateMetadata.get().keyName().matches("vespa.tls.default.default.*-key"));
        assertTrue(endpointCertificateMetadata.get().certName().matches("vespa.tls.default.default.*-cert"));
        assertEquals(0, endpointCertificateMetadata.get().version());
    }

    @Test
    public void reuses_stored_certificate_metadata() {
        mockCuratorDb.writeEndpointCertificateMetadata(testInstance.id(), new EndpointCertificateMetadata(testKeyName, testCertName, 7));
        Optional<EndpointCertificateMetadata> endpointCertificateMetadata = endpointCertificateManager.getEndpointCertificateMetadata(testInstance, testZone);
        assertTrue(endpointCertificateMetadata.isPresent());
        assertEquals(testKeyName, endpointCertificateMetadata.get().keyName());
        assertEquals(testCertName, endpointCertificateMetadata.get().certName());
        assertEquals(7, endpointCertificateMetadata.get().version());
    }

    @Test
    public void uses_refreshed_certificate_when_available_and_valid() {
        inMemoryFlagSource.withBooleanFlag(Flags.USE_REFRESHED_ENDPOINT_CERTIFICATE.id(), true);

        secretStore.setSecret(testKeyName, "secret-key", 7);
        secretStore.setSecret(testCertName, "cert", 7);
        secretStore.setSecret(testKeyName, KeyUtils.toPem(testKeyPair.getPrivate()), 8);
        secretStore.setSecret(testCertName, X509CertificateUtils.toPem(testCertificate)+X509CertificateUtils.toPem(testCertificate), 8);
        mockCuratorDb.writeEndpointCertificateMetadata(testInstance.id(), new EndpointCertificateMetadata(testKeyName, testCertName, 7));
        Optional<EndpointCertificateMetadata> endpointCertificateMetadata = endpointCertificateManager.getEndpointCertificateMetadata(testInstance, testZone);
        assertTrue(endpointCertificateMetadata.isPresent());
        assertEquals(testKeyName, endpointCertificateMetadata.get().keyName());
        assertEquals(testCertName, endpointCertificateMetadata.get().certName());
        assertEquals(8, endpointCertificateMetadata.get().version());
    }

}
