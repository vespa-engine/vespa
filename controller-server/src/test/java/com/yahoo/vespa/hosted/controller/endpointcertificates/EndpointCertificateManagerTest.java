package com.yahoo.vespa.hosted.controller.endpointcertificates;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.Instance;
import com.yahoo.vespa.hosted.controller.api.integration.certificates.ApplicationCertificateMock;
import com.yahoo.vespa.hosted.controller.api.integration.certificates.EndpointCertificateMetadata;
import com.yahoo.vespa.hosted.controller.integration.SecretStoreMock;
import com.yahoo.vespa.hosted.controller.integration.ZoneRegistryMock;
import com.yahoo.vespa.hosted.controller.persistence.MockCuratorDb;
import org.junit.Test;

import java.time.Clock;
import java.util.Optional;

import static org.junit.Assert.assertTrue;

public class EndpointCertificateManagerTest {

    @Test
    public void getEndpointCertificate() {
        SecretStoreMock secretStore = new SecretStoreMock();
        ZoneRegistryMock zoneRegistryMock = new ZoneRegistryMock(SystemName.main);
        MockCuratorDb mockCuratorDb = new MockCuratorDb();
        ApplicationCertificateMock applicationCertificateMock = new ApplicationCertificateMock();
        Clock clock = Clock.systemUTC();
        EndpointCertificateManager endpointCertificateManager = new EndpointCertificateManager(zoneRegistryMock, mockCuratorDb, secretStore, applicationCertificateMock, clock);
        ZoneId id = zoneRegistryMock.zones().directlyRouted().zones().stream().findFirst().get().getId();
        Instance instance = new Instance(ApplicationId.defaultId());
        Optional<EndpointCertificateMetadata> endpointCertificateMetadata = endpointCertificateManager.getEndpointCertificateMetadata(instance, id);
        assertTrue(endpointCertificateMetadata.isPresent());
    }
}