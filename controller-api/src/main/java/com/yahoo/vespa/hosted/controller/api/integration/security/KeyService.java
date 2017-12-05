// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.security;

/**
 * A service for retrieving secrets, such as API keys, private keys and passwords.
 *
 * @author mpolden
 * @author bjorncs
 */
public interface KeyService {

    String getSecret(String key);

    default String getSecret(String key, int version) {
        throw new UnsupportedOperationException("KeyService implementation does not support versioned secrets");
    }

}
