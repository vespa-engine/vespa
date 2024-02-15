// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.config.server.tenant;

import com.yahoo.config.model.api.EndpointCertificateMetadata;
import com.yahoo.config.model.api.EndpointCertificateSecretStore;
import com.yahoo.container.jdisc.secretstore.SecretNotFoundException;
import com.yahoo.container.jdisc.secretstore.SecretStore;

import javax.inject.Inject;
import java.util.Optional;

public class DefaultEndpointCertificateSecretStore extends EndpointCertificateSecretStore {

    private final SecretStore secretStore;

    @Inject
    public DefaultEndpointCertificateSecretStore(SecretStore secretStore) {
        this.secretStore = secretStore;
    }

    
    @Override
    public Optional<String> getPrivateKey(EndpointCertificateMetadata metadata) {
        return getValue(metadata.keyName(), metadata.version());
    }

    @Override
    public Optional<String> getCertificate(EndpointCertificateMetadata metadata) {
        return getValue(metadata.certName(), metadata.version());
    }

    private Optional<String> getValue(String key, int version) {
        try {
            return Optional.ofNullable(secretStore.getSecret(key, version));
        } catch (SecretNotFoundException e) {
            return Optional.empty();
        }
    }
    @Override
    public boolean supports(EndpointCertificateMetadata.Provider provider) {
        return provider == EndpointCertificateMetadata.Provider.digicert || provider == EndpointCertificateMetadata.Provider.globalsign;
    }
}
