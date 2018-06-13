// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.plugin.util;

import java.util.Optional;

/**
 * @author Tony Vaagenes
 * @author ollivir
 */
public class Strings {
    public static String replaceEmptyString(String s, String replacement) {
        if (s == null || s.isEmpty()) {
            return replacement;
        } else {
            return s;
        }
    }

    public static Optional<String> noneIfEmpty(String s) {
        if (s == null || s.isEmpty()) {
            return Optional.empty();
        } else {
            return Optional.of(s);
        }
    }
}