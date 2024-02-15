// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.config.model.api;

import java.util.List;
import java.util.Optional;

public abstract class EndpointCertificateSecretStore {

    public final EndpointCertificateSecrets getSecret(EndpointCertificateMetadata metadata) {
        Optional<String> certificate = getCertificate(metadata);
        Optional<String> key = getPrivateKey(metadata);
        if (certificate.isPresent() && key.isPresent()) {
            return new EndpointCertificateSecrets(certificate.get(), key.get(), metadata.version());
        } else {
            return EndpointCertificateSecrets.missing(metadata.version());
        }
    }

    public abstract Optional<String> getPrivateKey(EndpointCertificateMetadata metadata);
    public abstract Optional<String> getCertificate(EndpointCertificateMetadata metadata);

    public abstract boolean supports(EndpointCertificateMetadata.Provider provider);

}
