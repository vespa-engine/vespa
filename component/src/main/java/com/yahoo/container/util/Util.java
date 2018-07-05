// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.util;

/**
 * TODO: What is this?
 *
 * @author Tony Vaagenes
 */
// TODO: Move to a a more appropriate package in vespajlib
// TODO: Fix name
public class Util {

    // TODO: What is this?
    @SafeVarargs
    public static <T> T firstNonNull(T... args) {
        for (T arg : args) {
            if (arg != null)
                return arg;
        }
        return null;
    }

    // TODO: What is this?
    public static String quote(Object object) {
        return "'" + object + "'";
    }
}
