// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.dataplanetoken;

/**
 * Represents a generated data plane token.
 *
 * Note: This _MUST_ not be persisted.
 *
 * @author mortent
 */
public record DataplaneToken(TokenId tokenId, FingerPrint fingerPrint, String tokenValue) {
}
