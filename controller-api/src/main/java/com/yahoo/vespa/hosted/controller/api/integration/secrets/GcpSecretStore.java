package com.yahoo.vespa.hosted.controller.api.integration.secrets;

import java.time.Duration;
import java.util.Optional;

public interface GcpSecretStore {

    void setSecret(String secretName, Optional<Duration> expiry, String... accessorServiceAccounts);

    void addSecretVersion(String secretName, String secretValue);

    String getLatestSecretVersion(String secretName);

}
