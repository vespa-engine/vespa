// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.language.lucene;

import com.yahoo.language.Language;
import com.yahoo.language.process.StemMode;

/**
 * An analyzer key: A profile, language and a stem mode. All values may be null.
 *
 * @author bratseth
 */
record AnalyzerKey(String profile, Language language, StemMode stemMode) {

    AnalyzerKey withProfile(String profile) {
        return new AnalyzerKey(profile, language, stemMode);
    }

    AnalyzerKey withLanguage(Language language) {
        return new AnalyzerKey(profile, language, stemMode);
    }

    AnalyzerKey withStemMode(StemMode stemMode) {
        return new AnalyzerKey(profile, language, stemMode);
    }

    static AnalyzerKey fromString(String s) {
        if (s == null || s.isEmpty()) return new AnalyzerKey(null, null, null);

        if ( ! s.contains("=")) { // A "language" or "language/stem-mode
            String[] parts = s.split("/");
            if (parts.length == 1)
                return new AnalyzerKey(null, Language.fromLanguageTag(parts[0]), null);
            if (parts.length == 2)
                return new AnalyzerKey(null, Language.fromLanguageTag(parts[0]), StemMode.valueOf(parts[1]));
            throw new IllegalArgumentException("Illegal analyzer key '" + s + "'");
        }
        else { // general "part=value;..." syntax
            String profile = null;
            Language language = null;
            StemMode stemMode = null;
            for (var part : s.split(";")) {
                String[] keyValue = part.trim().split("=");
                if (keyValue.length != 2)
                    throw new IllegalArgumentException("Illegal analyzer key '" + s + "': Pars must be separated by ';', " +
                                                       "and each value part must contain one 'key=value'");
                switch (keyValue[0]) {
                    case "profile" :
                        profile = keyValue[1];
                        break;
                    case "language" :
                        language = Language.fromLanguageTag(keyValue[1]);
                        break;
                    case "stemMode" :
                        stemMode = StemMode.valueOf(keyValue[1]);
                        break;
                    default:
                        throw new IllegalArgumentException("Illegal analyzer key '" + s +
                                                           "': Value names must be 'profile', 'language' or 'stemMode'");
                }
            }
            return new AnalyzerKey(profile, language, stemMode);
        }
    }

}

