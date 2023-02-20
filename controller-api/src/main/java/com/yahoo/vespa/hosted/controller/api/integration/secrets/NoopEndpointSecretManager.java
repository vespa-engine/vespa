package com.yahoo.vespa.hosted.controller.api.integration.secrets;

public class NoopEndpointSecretManager implements EndpointSecretManager {
    @Override
    public void deleteSecret(String secretName) {
        // noop
    }
}
