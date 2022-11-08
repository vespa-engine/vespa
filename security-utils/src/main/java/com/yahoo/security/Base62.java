// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.security;

/**
 * Base62 encoding which has the nice property that it does not feature any
 * potential word/line-breaking characters, which means encoded strings can
 * usually be selected in one go on web pages or in the terminal.
 *
 * @see <a href="https://en.wikipedia.org/wiki/Base62">Base62 on Wiki</a>
 *
 * @author vekterli
 */
public class Base62 {

    private static final BaseNCodec INSTANCE = BaseNCodec.of("0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz");

    public static BaseNCodec codec() {
        return INSTANCE;
    }

}
