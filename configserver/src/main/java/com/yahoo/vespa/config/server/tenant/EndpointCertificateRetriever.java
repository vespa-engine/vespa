// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.tenant;

import com.yahoo.config.model.api.EndpointCertificateMetadata;
import com.yahoo.config.model.api.EndpointCertificateSecrets;
import com.yahoo.container.jdisc.secretstore.SecretStore;
import com.yahoo.security.KeyUtils;
import com.yahoo.security.X509CertificateUtils;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Used to retrieve actual endpoint certificate/key from secret store.
 *
 * @author andreer
 */
public record EndpointCertificateRetriever(SecretStore secretStore) {

    private static final Logger log = Logger.getLogger(EndpointCertificateRetriever.class.getName());

    public Optional<EndpointCertificateSecrets> readEndpointCertificateSecrets(EndpointCertificateMetadata metadata) {
        return Optional.of(readFromSecretStore(metadata));
    }

    private EndpointCertificateSecrets readFromSecretStore(EndpointCertificateMetadata endpointCertificateMetadata) {
        try {
            String cert = secretStore.getSecret(endpointCertificateMetadata.certName(), endpointCertificateMetadata.version());
            String key = secretStore.getSecret(endpointCertificateMetadata.keyName(), endpointCertificateMetadata.version());

            verifyKeyMatchesCertificate(endpointCertificateMetadata, cert, key);

            return new EndpointCertificateSecrets(cert, key, endpointCertificateMetadata.version());
        } catch (RuntimeException e) {
            log.log(Level.WARNING, "Exception thrown during certificate retrieval", e);
            // Assume not ready yet
            return EndpointCertificateSecrets.missing(endpointCertificateMetadata.version());
        }
    }

    private void verifyKeyMatchesCertificate(EndpointCertificateMetadata endpointCertificateMetadata, String cert, String key) {
        X509Certificate x509Certificate = X509CertificateUtils.fromPem(cert);

        PrivateKey privateKey = KeyUtils.fromPemEncodedPrivateKey(key);
        PublicKey publicKey = x509Certificate.getPublicKey();

        if(!X509CertificateUtils.privateKeyMatchesPublicKey(privateKey, publicKey)) {
            throw new IllegalArgumentException("Failed to retrieve endpoint secrets: Certificate and key data do not match for " + endpointCertificateMetadata);
        }
    }
}
