package com.yahoo.vespa.hosted.controller.api.integration.secrets;

public interface EndpointSecretManager {
    void deleteSecret(String secretName);
}
