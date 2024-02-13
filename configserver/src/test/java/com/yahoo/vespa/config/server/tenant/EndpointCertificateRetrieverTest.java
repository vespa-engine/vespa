// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.config.server.tenant;

import com.yahoo.config.model.api.EndpointCertificateMetadata;
import com.yahoo.config.model.api.EndpointCertificateSecretStore;
import com.yahoo.config.model.api.EndpointCertificateSecrets;
import com.yahoo.security.KeyAlgorithm;
import com.yahoo.security.KeyUtils;
import com.yahoo.security.SignatureAlgorithm;
import com.yahoo.security.X509CertificateBuilder;
import com.yahoo.security.X509CertificateUtils;
import com.yahoo.vespa.config.server.MockSecretStore;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import javax.security.auth.x500.X500Principal;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

public class EndpointCertificateRetrieverTest {


    private final KeyPair keyPair = KeyUtils.generateKeypair(KeyAlgorithm.EC, 256);
    private final X509Certificate digicertCertificate = X509CertificateBuilder.fromKeypair(keyPair, new X500Principal("CN=digicert"),
            Instant.now(), Instant.now().plus(1, ChronoUnit.DAYS), SignatureAlgorithm.SHA512_WITH_ECDSA, BigInteger.valueOf(12345)).build();

    private final X509Certificate zerosslCertificate = X509CertificateBuilder.fromKeypair(keyPair, new X500Principal("CN=zerossl"),
            Instant.now(), Instant.now().plus(1, ChronoUnit.DAYS), SignatureAlgorithm.SHA512_WITH_ECDSA, BigInteger.valueOf(12345)).build();


    @Test
    void reads_from_correct_endpoint_certificate_store() {
        MockSecretStore secretStore = new MockSecretStore();
        secretStore.put("cert", 1, X509CertificateUtils.toPem(digicertCertificate));
        secretStore.put("key", 1, KeyUtils.toPem(keyPair.getPrivate()));
        DefaultEndpointCertificateSecretStore defaultEndpointCertificateSecretStore = new DefaultEndpointCertificateSecretStore(secretStore);
        TestEndpointCertificateSecretStore zerosslStore = new TestEndpointCertificateSecretStore(X509CertificateUtils.toPem(zerosslCertificate), KeyUtils.toPem(keyPair.getPrivate()));

        EndpointCertificateRetriever retriever = new EndpointCertificateRetriever(List.of(defaultEndpointCertificateSecretStore, zerosslStore));

        {
            Optional<EndpointCertificateSecrets> endpointCertificateSecrets = retriever.readEndpointCertificateSecrets(
                    new EndpointCertificateMetadata("key", "cert", 1, EndpointCertificateMetadata.Provider.digicert));
            Assertions.assertTrue(endpointCertificateSecrets.isPresent());
            Assertions.assertEquals("CN=digicert", X509CertificateUtils.fromPem(endpointCertificateSecrets.get().certificate()).getSubjectX500Principal().getName());
        }
        {
            Optional<EndpointCertificateSecrets> endpointCertificateSecrets = retriever.readEndpointCertificateSecrets(
                    new EndpointCertificateMetadata("key", "cert", 1, EndpointCertificateMetadata.Provider.zerossl));
            Assertions.assertTrue(endpointCertificateSecrets.isPresent());
            Assertions.assertEquals("CN=zerossl", X509CertificateUtils.fromPem(endpointCertificateSecrets.get().certificate()).getSubjectX500Principal().getName());
        }
    }

    @Test
    void returns_missing_when_cert_version_not_found() {
        DefaultEndpointCertificateSecretStore defaultEndpointCertificateSecretStore = new DefaultEndpointCertificateSecretStore(new MockSecretStore());
        TestEndpointCertificateSecretStore zerosslStore = new TestEndpointCertificateSecretStore(null, null);
        EndpointCertificateRetriever retriever = new EndpointCertificateRetriever(List.of(defaultEndpointCertificateSecretStore, zerosslStore));
        {
            Optional<EndpointCertificateSecrets> endpointCertificateSecrets = retriever.readEndpointCertificateSecrets(
                    new EndpointCertificateMetadata("key", "cert", 1, EndpointCertificateMetadata.Provider.digicert));
            Assertions.assertTrue(endpointCertificateSecrets.isPresent());
            Assertions.assertTrue(endpointCertificateSecrets.get().isMissing());
        }
        {
            Optional<EndpointCertificateSecrets> endpointCertificateSecrets = retriever.readEndpointCertificateSecrets(
                    new EndpointCertificateMetadata("key", "cert", 1, EndpointCertificateMetadata.Provider.zerossl));
            Assertions.assertTrue(endpointCertificateSecrets.isPresent());
            Assertions.assertTrue(endpointCertificateSecrets.get().isMissing());
        }
    }

    private static class TestEndpointCertificateSecretStore extends EndpointCertificateSecretStore {

        private final String certificate;
        private final String privatekey;

        public TestEndpointCertificateSecretStore(String certificate, String privatekey) {
            this.certificate = certificate;
            this.privatekey = privatekey;
        }

        @Override
        public Optional<String> getPrivateKey(EndpointCertificateMetadata metadata) {
            return Optional.ofNullable(privatekey);
        }

        @Override
        public Optional<String> getCertificate(EndpointCertificateMetadata metadata) {
            return Optional.ofNullable(certificate);
        }

        @Override
        public boolean supports(EndpointCertificateMetadata.Provider provider) {
            return provider == EndpointCertificateMetadata.Provider.zerossl;
        }
    }
}
