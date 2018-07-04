// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.language.process;

import com.yahoo.language.Language;
import com.yahoo.language.Linguistics;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * @author Simon Thoresen Hult
 */
public abstract class AbstractTokenizerTestCase {

    private boolean accentDrop = false;
    private Language language = Language.ENGLISH;
    private Linguistics linguistics;
    private StemMode stemMode = StemMode.NONE;

    public void assertTokenStrings(String input, List<String> expectedTokenStrings) {
        List<String> actual = new ArrayList<>();
        for (Token token : tokenize(input)) {
            findTokenStrings(token, actual);
        }
        assertEquals(expectedTokenStrings, actual);
    }

    public List<String> findTokenStrings(Token token, List<String> out) {
        int numComponents = token.getNumComponents();
        if (token.isSpecialToken() || numComponents == 0) {
            out.add(token.getTokenString());
        } else {
            for (int i = 0; i < numComponents; ++i) {
                findTokenStrings(token.getComponent(i), out);
            }
        }
        return out;
    }

    public Iterable<Token> tokenize(String input) {
        return linguistics.getTokenizer().tokenize(input, language, stemMode, accentDrop);
    }

    public AbstractTokenizerTestCase setAccentDrop(boolean accentDrop) {
        this.accentDrop = accentDrop;
        return this;
    }

    public AbstractTokenizerTestCase setLanguage(Language language) {
        this.language = language;
        return this;
    }

    public AbstractTokenizerTestCase setLinguistics(Linguistics linguistics) {
        this.linguistics = linguistics;
        return this;
    }

    public AbstractTokenizerTestCase setStemMode(StemMode stemMode) {
        this.stemMode = stemMode;
        return this;
    }

}
