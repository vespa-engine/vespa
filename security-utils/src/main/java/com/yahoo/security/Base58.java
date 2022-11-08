// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.security;

/**
 * Base58 encoding using the alphabet standardized by Bitcoin et al., which avoids
 * the use of characters [0OIl] to avoid visual ambiguity. It does not feature any
 * potential word/line-breaking characters, which means encoded strings can usually
 * be selected in one go on web pages or in the terminal.
 *
 * @see <a href="https://en.wikipedia.org/wiki/Base58">Base58 on Wiki</a>
 *
 * @author vekterli
 */
public class Base58 {

    private static final BaseNCodec INSTANCE = BaseNCodec.of("123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz");

    public static BaseNCodec codec() {
        return INSTANCE;
    }

}
