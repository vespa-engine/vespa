// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.dataplanetoken;

import ai.vespa.validation.PatternedStringWrapper;

import java.util.regex.Pattern;

/**
 * A token id to be used in dataplane tokens
 */
public class TokenId extends PatternedStringWrapper<TokenId> {

    static final Pattern namePattern = Pattern.compile("[A-Za-z][A-Za-z0-9_-]{0,59}");

    private TokenId(String name) {
        super(name, namePattern, "tokenId");
    }

    public static TokenId of(String value) {
        return new TokenId(value);
    }

}
