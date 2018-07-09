// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.athenz;

import com.yahoo.vespa.athenz.api.AthenzService;

import java.security.PublicKey;
import java.util.Optional;

/**
 * @author bjorncs
 */
public interface ZmsKeystore {

    Optional<PublicKey> getPublicKey(AthenzService service, String keyId);

    default void preloadKeys(AthenzService service) { /* Default implementation is noop */ }

}
