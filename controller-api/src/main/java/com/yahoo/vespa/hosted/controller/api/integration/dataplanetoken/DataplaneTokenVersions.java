// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.dataplanetoken;

import java.time.Instant;
import java.util.List;

/**
 * List of dataplane token versions of a token id.
 *
 * @author mortent
 */
public record DataplaneTokenVersions(TokenId tokenId, List<Version> tokenVersions) {
    public record Version(FingerPrint fingerPrint, String checkAccessHash, Instant creationTime, String author) {
    }
}
