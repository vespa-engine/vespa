package com.yahoo.vespa.hosted.controller.api.integration.secrets;

public interface GcpSecretStore {

    void createSecret(String secretName, String secret);

    String getSecret(String secretName, int version);
}
