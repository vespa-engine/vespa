// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.text.internal;

/**
 * Truncate text to a snippet suitable for logging.
 *
 * @author hakon
 */
public class SnippetGenerator {

    private static final String OMIT_PREFIX = "[...";
    private static final String OMIT_SUFFIX = " chars omitted]";
    private static final int ASSUMED_OMIT_TEXT_LENGTH = OMIT_PREFIX.length() + 4 + OMIT_SUFFIX.length();

    /** Returns a snippet of approximate size. */
    public String makeSnippet(String text, int sizeHint) {
        if (text.length() <= Math.max(sizeHint, ASSUMED_OMIT_TEXT_LENGTH)) return text;

        int maxSuffixLength = Math.max(0, (sizeHint - ASSUMED_OMIT_TEXT_LENGTH) / 2);
        int maxPrefixLength = Math.max(0, sizeHint - ASSUMED_OMIT_TEXT_LENGTH - maxSuffixLength);
        String sizeString = Integer.toString(text.length() - maxPrefixLength - maxSuffixLength);

        // It would be silly to return a snippet when the full text is barely longer.
        // Note: Say ASSUMED_OMIT_TEXT_LENGTH=23: text will be returned whenever sizeHint<23 and text.length()<28.
        int snippetLength = maxPrefixLength + OMIT_PREFIX.length() + sizeString.length() + OMIT_SUFFIX.length() + maxSuffixLength;
        if (text.length() <= 1.05 * snippetLength + 5) return text;

        return text.substring(0, maxPrefixLength) +
                OMIT_PREFIX +
                sizeString +
                OMIT_SUFFIX +
                text.substring(text.length() - maxSuffixLength);
    }
}
