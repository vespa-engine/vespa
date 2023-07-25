// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.secrets;

import java.time.Duration;
import java.util.Optional;

/**
 * @author olaa
 */
public class NoopGcpSecretStore implements GcpSecretStore {

    @Override
    public void setSecret(String secretName, Optional<Duration> expiry, String... accessorServiceAccounts) { }

    @Override
    public void addSecretVersion(String secretName, String secretValue) { }

    @Override
    public String getLatestSecretVersion(String secretName) { return ""; }

}
