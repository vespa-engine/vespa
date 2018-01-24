// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.language;

import java.util.Locale;

/**
 * @author Simon Thoresen
 */
public final class LocaleFactory {

    private static final Locale UNKNOWN = new Locale("", "", "");

    private LocaleFactory() {
        // hide
    }

    /**
     * Implements a simple parser for RFC5646 language tags. The language tag is parsed into a Locale.
     *
     * @param tag The language tag to parse.
     * @return The corrseponding Locale.
     */
    @SuppressWarnings("ConstantConditions")
    public static Locale fromLanguageTag(String tag) {
        // TODO: Should be replaced by return Locale.forLanguageTag(tag); ?

        tag.getClass(); // throws NullPointerException
        tag = tag.trim();
        if (tag.isEmpty()) {
            return UNKNOWN;
        }
        String language = "";
        String region = "";
        String script = "";
        String[] parts = tag.split("-");
        for (int partIdx = 0; partIdx < parts.length; ++partIdx) {
            String part = parts[partIdx];
            int partLen = part.length();
            if (partIdx == 0) {
                if (partLen == 2 || partLen == 3) {
                    language = part;
                }
            } else if (partIdx == 1 || partIdx == 2) {
                if (partLen == 2 || partLen == 3) {
                    region = part;
                } else if (partLen == 4) {
                    script = part;
                }
            }
        }
        if (language.isEmpty()) {
            return UNKNOWN;
        }
        return new Locale(language, region, script);
    }

}
