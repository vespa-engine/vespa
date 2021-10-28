// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.tenant;

import com.yahoo.config.model.api.EndpointCertificateMetadata;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.path.Path;
import com.yahoo.security.KeyAlgorithm;
import com.yahoo.security.KeyUtils;
import com.yahoo.security.SignatureAlgorithm;
import com.yahoo.security.X509CertificateBuilder;
import com.yahoo.security.X509CertificateUtils;
import com.yahoo.vespa.config.server.MockSecretStore;
import com.yahoo.vespa.curator.mock.MockCurator;
import org.junit.Before;
import org.junit.Test;

import javax.security.auth.x500.X500Principal;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class EndpointCertificateMetadataStoreTest {

    private static final Path tenantPath = Path.createRoot();
    private static final Path endpointCertificateMetadataPath = Path.createRoot().append("tlsSecretsKeys").append("default:test:default");
    private static final ApplicationId applicationId = ApplicationId.from(TenantName.defaultName(),
            ApplicationName.from("test"), InstanceName.defaultName());

    private MockCurator curator;
    private final MockSecretStore secretStore = new MockSecretStore();
    private EndpointCertificateMetadataStore endpointCertificateMetadataStore;
    private EndpointCertificateRetriever endpointCertificateRetriever;
    private final KeyPair keyPair = KeyUtils.generateKeypair(KeyAlgorithm.EC, 256);
    private final X509Certificate certificate = X509CertificateBuilder.fromKeypair(keyPair, new X500Principal("CN=subject"),
                                                                                   Instant.now(), Instant.now().plus(1, ChronoUnit.DAYS), SignatureAlgorithm.SHA512_WITH_ECDSA, BigInteger.valueOf(12345)).build();

    @Before
    public void setUp() {
        curator = new MockCurator();
        endpointCertificateMetadataStore = new EndpointCertificateMetadataStore(curator, tenantPath);
        endpointCertificateRetriever = new EndpointCertificateRetriever(secretStore);

        secretStore.put("vespa.tlskeys.tenant1--app1-cert", X509CertificateUtils.toPem(certificate));
        secretStore.put("vespa.tlskeys.tenant1--app1-key", KeyUtils.toPem(keyPair.getPrivate()));
    }

    @Test
    public void reads_object_format() {
        curator.set(endpointCertificateMetadataPath,
                "{\"keyName\": \"vespa.tlskeys.tenant1--app1-key\", \"certName\":\"vespa.tlskeys.tenant1--app1-cert\", \"version\": 0}"
                        .getBytes());

        // Read from zk and verify cert and key are available
        var secrets = endpointCertificateMetadataStore.readEndpointCertificateMetadata(applicationId)
                .flatMap(endpointCertificateRetriever::readEndpointCertificateSecrets);
        assertTrue(secrets.isPresent());
        assertTrue(secrets.get().key().startsWith("-----BEGIN EC PRIVATE KEY"));
        assertTrue(secrets.get().certificate().startsWith("-----BEGIN CERTIFICATE"));
    }

    @Test
    public void can_write_object_format() {
        var endpointCertificateMetadata = new EndpointCertificateMetadata("key-name", "cert-name", 1);

        endpointCertificateMetadataStore.writeEndpointCertificateMetadata(applicationId, endpointCertificateMetadata);

        assertEquals("{\"keyName\":\"key-name\",\"certName\":\"cert-name\",\"version\":1}",
                new String(curator.getData(endpointCertificateMetadataPath).orElseThrow()));
    }
}
