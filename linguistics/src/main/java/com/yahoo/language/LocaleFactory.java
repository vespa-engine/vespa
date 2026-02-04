// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.language;

import com.yahoo.text.Text;

import java.util.IllformedLocaleException;
import java.util.Locale;
import java.util.Objects;

/**
 * @author Simon Thoresen Hult
 */
public final class LocaleFactory {

    private static final Locale UNKNOWN = Locale.ROOT;

    private LocaleFactory() {}

    /**
     * Implements a simple parser for RFC5646 language tags. The language tag is parsed into a Locale.
     *
     * @param tag the language tag to parse
     * @return the corresponding Locale
     */
    public static Locale fromLanguageTag(String tag) {
        Objects.requireNonNull(tag, "tag cannot be null");
        // Tags can have variants and extension making them arbitrarily long,
        // but tags exceeding 35-40 characters are extremely rare in production systems.
        // Truncate in case people mistakenly use large text fields as tags
        var truncatedTag = Text.truncate(tag, 45);
        // truncate adds whitespace, so check original tag for whitespace
        if (tag.contains(" ")) {
            throw new IllegalArgumentException("Illegal language tag '" + truncatedTag + "', language tags corresponding to RFC5646 cannot contain whitespace");
        }
        tag = truncatedTag;

        tag = tag.trim();
        if (tag.isEmpty()) return UNKNOWN;

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
        if (language.isEmpty()) return UNKNOWN;
        try {
            return new Locale.Builder().setLanguage(language).setScript(script).setRegion(region).build();
        } catch (IllformedLocaleException e) {
            throw new IllegalArgumentException("Illegal language tag '" + tag + "', it must be a language tag corresponding to RFC5646");
        }
    }

}
