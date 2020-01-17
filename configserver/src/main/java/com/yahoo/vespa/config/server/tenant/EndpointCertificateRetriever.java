// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.tenant;

import com.yahoo.config.model.api.EndpointCertificateMetadata;
import com.yahoo.config.model.api.EndpointCertificateSecrets;
import com.yahoo.container.jdisc.secretstore.SecretStore;

import java.util.Optional;

/**
 * Used to retrieve actual endpoint certificate/key from secret store.
 *
 * @author andreer
 */
public class EndpointCertificateRetriever {

    private final SecretStore secretStore;

    public EndpointCertificateRetriever(SecretStore secretStore) {
        this.secretStore = secretStore;
    }

    public Optional<EndpointCertificateSecrets> readEndpointCertificateSecrets(EndpointCertificateMetadata metadata) {
        return Optional.of(readFromSecretStore(metadata));
    }

    private EndpointCertificateSecrets readFromSecretStore(EndpointCertificateMetadata endpointCertificateMetadata) {
        try {
            String cert = secretStore.getSecret(endpointCertificateMetadata.certName(), endpointCertificateMetadata.version());
            String key = secretStore.getSecret(endpointCertificateMetadata.keyName(), endpointCertificateMetadata.version());
            return new EndpointCertificateSecrets(cert, key);
        } catch (RuntimeException e) {
            // Assume not ready yet
            return EndpointCertificateSecrets.MISSING;
        }
    }
}
