// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.athens;

import java.security.PublicKey;
import java.util.Optional;

/**
 * Interface for a keystore containing public keys for Athens services
 *
 * @author bjorncs
 */
@FunctionalInterface
public interface ZmsKeystore {
    Optional<PublicKey> getPublicKey(AthensService service, String keyId);

    default void preloadKeys(AthensService service) {
        // Default implementation is noop
    }
}
