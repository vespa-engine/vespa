// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.log;

/**
 * @author lulf
 * @since 5.1
 */
class LogUtil {
    static boolean empty(String s) {
        return (s == null || s.equals(""));
    }
}
