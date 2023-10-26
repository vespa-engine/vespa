// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation;

import java.util.ArrayList;
import java.util.List;

/**
 * Will tokenize based on the delimiters while dequoting any qouted text.
 * @author baldersheim
 */
public class TokenizeAndDeQuote {
    private static final char ESCAPE = '\\';
    private final String delims;
    private final String quotes;

    public TokenizeAndDeQuote(String delims,String quotes) {
        this.delims = delims;
        this.quotes = quotes;
    }

    public List<String> tokenize(String string) {
        StringBuilder current = new StringBuilder();
        List<String> tokens = new ArrayList<>();
        char quote = 0;

        for (int i = 0; i < string.length(); i++) {
            char c = string.charAt(i);

            if ((c == ESCAPE) && (i + 1 < string.length())) {
                // Escaped, append next char
                current.append(string.charAt(++i));
            } else if ((quote == 0) && (delims.indexOf(c) >=0 )) {
                // Delimiter found outside quoted section, add token and start next
                tokens.add(current.toString());
                current.setLength(0);
            } else if ((quote == 0) && (quotes.indexOf(c) >= 0)) {
                // Start of quote
                quote = c;
            } else if (quote == c) {
                // End of quote
                quote = 0;
            } else {
                current.append(c);
            }
        }

        if ( ! current.isEmpty()) {
            // And then the last token if any
            tokens.add(current.toString());
        }

        return tokens;
    }
}
