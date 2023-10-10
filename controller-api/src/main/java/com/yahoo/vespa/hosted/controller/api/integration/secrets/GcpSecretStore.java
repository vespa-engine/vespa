// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.secrets;

import java.time.Duration;
import java.util.Optional;

public interface GcpSecretStore {

    void setSecret(String secretName, Optional<Duration> expiry, String... accessorServiceAccounts);

    void addSecretVersion(String secretName, String secretValue);

    String getLatestSecretVersion(String secretName);

}
