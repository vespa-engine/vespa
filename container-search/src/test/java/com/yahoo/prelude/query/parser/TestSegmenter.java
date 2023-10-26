// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query.parser;

import com.yahoo.language.Language;
import com.yahoo.language.process.Segmenter;

import java.util.List;

/**
 * @author bratseth
 */
public class TestSegmenter implements Segmenter {

    /**
     * <p>Splits "cd" and "fg" and every other single letter into separate tokens.</p>
     * <p/>
     * <p><b>Special case</b> for testing overlapping tokens:
     * Any occurence of the string "bcd" will <b>not</b> split into the tokens
     * "bc" and "d", but will instead split into "bc" and "cd".</p>
     */
    @Override
    public List<String> segment(String string, Language language) {
        List<String> tokens = new java.util.ArrayList<>();

        // Tokenize
        for (int i = 0; i < string.length(); i++) {
            String token = startsByTestToken(string, i);
            if (token != null) {
                tokens.add(token);
                i = i + token.length() - 1;
            } else {
                tokens.add(string.substring(i, i + 1));
            }
        }

        // Special case
        for (int i = 0; i < tokens.size(); i++) {
            String token = tokens.get(i);
            if (token.equals("bc") && tokens.size() > i + 1 && tokens.get(i + 1).equals("d")) {
                tokens.set(i + 1, "cd");
            }
        }

        return tokens;
    }

    private static final String[] testTokens = new String[] { "bc", "fg", "first", "second", "third" };

    private static String startsByTestToken(String string, int index) {
        for (String testToken : testTokens) {
            if (string.startsWith(testToken, index)) {
                return testToken;
            }
        }
        return null;
    }
}
