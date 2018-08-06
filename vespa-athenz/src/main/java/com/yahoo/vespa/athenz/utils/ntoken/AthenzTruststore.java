// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.utils.ntoken;

import java.security.PublicKey;
import java.util.Optional;

/**
 * A truststore that contains all ZMS and ZTS public keys
 *
 * @author bjorncs
 */
public interface AthenzTruststore {
   Optional<PublicKey> getZmsPublicKey(String keyId);
   Optional<PublicKey> getZtsPublicKey(String keyId);
}
