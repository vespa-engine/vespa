// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.select.parser;

import com.yahoo.javacc.UnicodeUtilities;

import java.math.BigInteger;

/**
 * @author Simon Thoresen Hult
 */
public class SelectParserUtils {

    static long decodeLong(String str) {
        if (str.startsWith("0x") || str.startsWith("0X")) {
            str = Long.toString(new BigInteger(str.substring(2), 16).longValue());
        }
        return Long.decode(str);
    }

    public static String quote(String str, char quote) {
        return UnicodeUtilities.quote(str, quote);
    }

    public static String unquote(String str) {
        return UnicodeUtilities.unquote(str);
    }
}
