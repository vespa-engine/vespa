// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.language.simple;

import com.yahoo.language.Language;
import com.yahoo.language.Linguistics;
import com.yahoo.language.process.LinguisticsParameters;
import com.yahoo.language.process.StemMode;
import com.yahoo.language.process.Token;
import com.yahoo.language.process.TokenScript;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * @author bratseth
 */
public class TokenizerTester {

    private boolean lowercase = true;
    private boolean accentDrop = false;
    private Language language = Language.ENGLISH;
    private Linguistics linguistics = new SimpleLinguistics();
    private StemMode stemMode = StemMode.NONE;

    public void assertTokens(String input, String ... expectedTokenStrings) {
        List<String> actual = new ArrayList<>();
        for (Token token : tokenize(input)) {
            findTokenStrings(token, actual);
        }
        assertEquals(List.of(expectedTokenStrings), actual);
    }

    public void assertTokenScripts(String input, TokenScript... expectedTokenScripts) {
        List<TokenScript> actual = new ArrayList<>();
        for (Token token : tokenize(input)) {
            findTokenScripts(token, actual);
        }
        assertEquals(List.of(expectedTokenScripts), actual);
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

    public List<TokenScript> findTokenScripts(Token token, List<TokenScript> out) {
        int numComponents = token.getNumComponents();
        if (token.isSpecialToken() || numComponents == 0) {
            out.add(token.getScript());
        } else {
            for (int i = 0; i < numComponents; ++i) {
                findTokenScripts(token.getComponent(i), out);
            }
        }
        return out;
    }

    public Iterable<Token> tokenize(String input) {
        var parameters = new LinguisticsParameters(language, stemMode, accentDrop, lowercase);
        return linguistics.getTokenizer().tokenize(input, parameters);
    }

    public TokenizerTester setLowercase(boolean lowercase) {
        this.lowercase = lowercase;
        return this;
    }

    public TokenizerTester setAccentDrop(boolean accentDrop) {
        this.accentDrop = accentDrop;
        return this;
    }

    public TokenizerTester setLanguage(Language language) {
        this.language = language;
        return this;
    }

    public TokenizerTester setLinguistics(Linguistics linguistics) {
        this.linguistics = linguistics;
        return this;
    }

    public TokenizerTester setStemMode(StemMode stemMode) {
        this.stemMode = stemMode;
        return this;
    }

}
