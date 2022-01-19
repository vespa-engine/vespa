// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.log;

/**
 * @author Ulf Lilleengen
 * @since 5.1
 * @deprecated Should only be used internally in the log library
 */
@Deprecated(since = "7", forRemoval = true)
class LogUtil {
    static boolean empty(String s) {
        return (s == null || s.equals(""));
    }
}
