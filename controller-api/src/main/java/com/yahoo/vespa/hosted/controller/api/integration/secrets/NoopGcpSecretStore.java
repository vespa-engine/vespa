// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.secrets;

/**
 * @author olaa
 */
public class NoopGcpSecretStore implements GcpSecretStore {

    @Override
    public void createSecret(String secretName, String secret) {

    }

    @Override
    public String getSecret(String secretName, int version) {
        return "";
    }
}
