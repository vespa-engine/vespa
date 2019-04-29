// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.certificates;

import com.yahoo.config.provision.ApplicationId;

/**
 * Provides a key pair. Generates and persists the key pair if not found.
 *
 * @author mortent
 * @author andreer
 */
public interface KeyPairProvider {
    VersionedKeyPair getKeyPair(ApplicationId applicationId);
}
