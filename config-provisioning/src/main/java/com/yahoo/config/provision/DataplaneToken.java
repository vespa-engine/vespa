// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Id, fingerprints and check access hashes of a data plane token
 *
 * @author mortent
 */
public record DataplaneToken(String tokenId, List<Version> versions) {

    public record Version(String fingerprint, String checkAccessHash, Optional<Instant> expiration) {
    }
}
