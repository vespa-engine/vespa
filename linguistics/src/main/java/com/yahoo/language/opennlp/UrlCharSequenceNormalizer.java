// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.language.opennlp;

import opennlp.tools.util.normalizer.CharSequenceNormalizer;

import java.util.regex.Pattern;

/**
 * Modifies {@link opennlp.tools.util.normalizer.UrlCharSequenceNormalizer} to avoid the bad email regex.
 *
 * @author jonmv
 */
public class UrlCharSequenceNormalizer implements CharSequenceNormalizer {

    private static final Pattern URL_REGEX =
            Pattern.compile("https?://[-_.?&~;+=/#0-9A-Za-z]+");
    private static final Pattern MAIL_REGEX =
            Pattern.compile("(?<![-+_.0-9A-Za-z])[-+_.0-9A-Za-z]+@[-0-9A-Za-z]+[-.0-9A-Za-z]+");

    private static final UrlCharSequenceNormalizer INSTANCE = new UrlCharSequenceNormalizer();

    public static UrlCharSequenceNormalizer getInstance() {
        return INSTANCE;
    }

    public CharSequence normalize(CharSequence text) {
        String modified = URL_REGEX.matcher(text).replaceAll(" ");
        return MAIL_REGEX.matcher(modified).replaceAll(" ");
    }

}
