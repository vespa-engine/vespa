// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.text;

import static com.yahoo.text.Lowercase.toLowerCase;

/**
 * Language helper functions.
 *
 * @deprecated do not use
 * @author  <a href="mailto:steinar@yahoo-inc.com">Steinar Knutsen</a>
 */
@Deprecated
public class LanguageHacks {

    /**
     * Whether a language is in the CJK group.
     */
    public static boolean isCJK(String language) {
        if (language == null) return false;

        language = toLowerCase(language);
        return "ja".equals(language)
                || "ko".equals(language)
                || language.startsWith("zh")
                || language.startsWith("tw"); // TODO: tw is a bogus value?
    }

    /**
     * Whether there is desegmenting in this language.
     */
    public static boolean yellDesegments(String language) {
        if (language == null) return false;

        language = toLowerCase(language);
        return "de".equals(language) || isCJK(language);
    }

}
