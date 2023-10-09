// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.language.process;

import com.yahoo.language.Language;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Simon Thoresen Hult
 */
public class StemmerImpl implements Stemmer {

    private final Tokenizer tokenizer;

    public StemmerImpl(Tokenizer tokenizer) {
        this.tokenizer = tokenizer;
    }

    @Override
    public List<StemList> stem(String input, StemMode stemMode, Language language) {
        List<StemList> stems = new ArrayList<>();
        for (Token token : tokenizer.tokenize(input, language, stemMode, false)) {
            findStems(token, stems);
        }
        return stems;
    }

    private void findStems(Token token, List<StemList> out) {
        int len;
        if (token.isSpecialToken() || (len = token.getNumComponents()) == 0) {
            if (token.isIndexable()) {
                StemList word = new StemList();
                word.add(token.getTokenString()); // takes care of getStem(0)
                for (int i = 1; i < token.getNumStems(); i++) {
                    word.add(token.getStem(i));
                }
                out.add(word);
            }
        } else {
            for (int i = 0; i < len; ++i) {
                findStems(token.getComponent(i), out);
            }
        }
    }

}
