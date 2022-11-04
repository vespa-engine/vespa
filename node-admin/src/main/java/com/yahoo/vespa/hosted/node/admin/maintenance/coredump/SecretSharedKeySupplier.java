// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.maintenance.coredump;

import com.yahoo.security.KeyId;
import com.yahoo.security.SecretSharedKey;

import java.util.Optional;

/**
 * @author vekterli
 */
@FunctionalInterface
public interface SecretSharedKeySupplier {

    Optional<SecretSharedKey> create(KeyId publicKeyId);

}
