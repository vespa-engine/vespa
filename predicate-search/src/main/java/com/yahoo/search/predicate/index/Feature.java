// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.predicate.index;

import com.yahoo.document.predicate.PredicateHash;

/**
 * Utility class for feature related constants and methods.
 *
 * @author bjorncs
 */
public class Feature {

    public static final String Z_STAR_COMPRESSED_ATTRIBUTE_NAME = "z-star-compressed";
    public static final long Z_STAR_COMPRESSED_ATTRIBUTE_HASH = PredicateHash.hash64(Z_STAR_COMPRESSED_ATTRIBUTE_NAME);

    private Feature() {}

    public static long createHash(String key, String value) {
        return PredicateHash.hash64(key + "=" + value);
    }

}
