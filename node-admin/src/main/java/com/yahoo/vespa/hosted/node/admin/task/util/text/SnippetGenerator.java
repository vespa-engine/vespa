// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.text;

/**
 * @author hakonhall
 */
public class SnippetGenerator {

    public static final String OMIT_PREFIX = "[...";
    public static final String OMIT_SUFFIX = " chars omitted]";
    /**
     * If {@code maxLength in }{@link #makeSnippet(String, int maxLength)} is less than this limit,
     * a snippet would actually be larger than maxLength.
     */
    public static final int LEAST_MAXLENGTH = OMIT_PREFIX.length() + 2 + OMIT_SUFFIX.length();

    public String makeSnippet(String possiblyHugeString, int maxLength) {
        if (possiblyHugeString.length() <= maxLength) {
            return possiblyHugeString;
        }

        // We will preserve a prefix and suffix, but replace the middle part of possiblyHugeString
        // with a cut text:
        //   possiblyHugeString = prefix + omitted + suffix
        //   result = prefix + cut + suffix
        //   cut = OMIT_PREFIX + size + OMIT_SUFFIX
        //   size = format("%d", omitted.length())
        //   digits = size.length()
        int assumedDigits = 2; // Just an initial guess: size.length() between 10 and 99
        int prefixLength, suffixLength;
        String size;

        while (true) {
            int assumedCutLength = OMIT_PREFIX.length() + assumedDigits + OMIT_SUFFIX.length();
            // Make prefixLength ~ suffixLength, with prefixLength >= suffixLength
            suffixLength = Math.max((maxLength - assumedCutLength) / 2, 0);
            prefixLength = Math.max(maxLength - assumedCutLength - suffixLength, 0);
            // RHS is guaranteed to be >= 0
            int omittedLength = possiblyHugeString.length() - prefixLength - suffixLength;
            size = String.format("%d", omittedLength);

            // If assumedCutLength happens to be wrong, we retry with an adjusted setting.
            int actualDigits = size.length();
            if (actualDigits == assumedDigits) {
                break;
            }

            // Is this loop guaranteed to finish? Yes, because from one iteration to the next,
            // omittedLength can change by at most 9 (size having 1 digit to size with 10 digits
            // or vice versa):
            //  - If actualDigits < assumedDigits, omittedLength will decrease on next iteration
            //    by 1-9, and so size can at most decrease by another 1 for that iteration. And,
            //    if it did decrease by 1, it cannot decrease again (and must therefore break
            //    the loop) in the iteration after, since a drop of (at most) 18 cannot remove
            //    2 digits from a number.
            //  - If actualDigits > assumedDigits, a similar argument holds.
            assumedDigits = actualDigits;
        }

        String snippet =
                possiblyHugeString.substring(0, prefixLength) +
                OMIT_PREFIX +
                size +
                OMIT_SUFFIX +
                possiblyHugeString.substring(possiblyHugeString.length() - suffixLength);

        if (snippet.length() > maxLength) {
            // This can happen if maxLength is too small.
            return possiblyHugeString.substring(0, maxLength);
        } else {
            return snippet;
        }
    }
}
