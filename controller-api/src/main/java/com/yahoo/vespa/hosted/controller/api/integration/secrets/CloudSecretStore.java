package com.yahoo.vespa.hosted.controller.api.integration.secrets;

public interface CloudSecretStore {
    // TODO andreer: give this a better name

    void createSecret(String secretName, String secret);

    String getSecret(String secretName, int version);
}
