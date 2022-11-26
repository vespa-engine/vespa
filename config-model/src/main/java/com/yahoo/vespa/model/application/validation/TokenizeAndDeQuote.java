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
        StringBuilder wordBuilder = new StringBuilder();
        List<String> words = new ArrayList<>();
        char quote = 0;

        for (int i = 0; i < string.length(); i++) {
            char c = string.charAt(i);

            if (c == ESCAPE && i + 1 < string.length()) {
                wordBuilder.append(string.charAt(++i));
            } else if (delims.indexOf(c) >=0 && quote == 0) {
                words.add(wordBuilder.toString());
                wordBuilder.setLength(0);
            } else if (quote == 0 && quotes.indexOf(c) >= 0) {
                quote = c;
            } else if (quote == c) {
                quote = 0;
            } else {
                wordBuilder.append(c);
            }
        }

        if (wordBuilder.length() > 0) {
            words.add(wordBuilder.toString());
        }

        return words;
    }
}
