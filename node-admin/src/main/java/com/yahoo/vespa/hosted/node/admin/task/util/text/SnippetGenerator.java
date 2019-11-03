// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.text;

/**
 * @author hakon
 */
public class SnippetGenerator {

    private static final int ASSUMED_OMIT_TEXT_LENGTH = omitText(1234).length();

    private static String omitText(int omittedLength) { return "[..." + omittedLength + " chars omitted]"; }

    /** Returns a snippet of approximate size. */
    public String makeSnippet(String text, int sizeHint) {
        if (text.length() < sizeHint) return text;

        int maxSuffixLength = Math.max(0, (sizeHint - ASSUMED_OMIT_TEXT_LENGTH) / 2);
        int maxPrefixLength = Math.max(0, sizeHint - ASSUMED_OMIT_TEXT_LENGTH - maxSuffixLength);
        String snippet =
                text.substring(0, maxPrefixLength) +
                omitText(text.length() - maxPrefixLength - maxSuffixLength) +
                text.substring(text.length() - maxSuffixLength);

        // It would be silly to return a snippet when the full text is barely longer.
        // Note: Say ASSUMED_OMIT_TEXT_LENGTH=23: text will be returned whenever sizeHint<23 and text.length()<28.
        if (text.length() <= 1.05 * snippet.length() + 5) return text;

        return snippet;
    }
}
