// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.secrets;

public class NoopEndpointSecretManager implements EndpointSecretManager {
    @Override
    public void deleteSecret(String secretName) {
        // noop
    }
}
