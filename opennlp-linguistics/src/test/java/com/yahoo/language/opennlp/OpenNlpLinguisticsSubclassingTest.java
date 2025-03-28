// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.language.opennlp;

import com.yahoo.language.Language;
import com.yahoo.language.process.LinguisticsParameters;
import com.yahoo.language.process.StemMode;
import com.yahoo.language.process.Token;
import com.yahoo.language.process.TokenType;
import com.yahoo.language.process.Tokenizer;
import com.yahoo.language.simple.SimpleToken;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * Verifies an observed pattern of subclassing OpenNlpLinguistics.
 */
public class OpenNlpLinguisticsSubclassingTest {

    @Test
    public void testOpenNlpLinguisticsSubclassing() {
        var parameters = new LinguisticsParameters(Language.ENGLISH, StemMode.ALL, true, true);
        var subclass = new OpenNlpLinguisticsSubclass();
        assertEquals("the only token", subclass.getTokenizer().tokenize("whatever", parameters).iterator().next().getTokenString());
        assertEquals("the only token", subclass.getSegmenter().segment("whatever", parameters.language()).iterator().next());
        assertEquals("the only token", subclass.getStemmer().stem("whatever", parameters).get(0).get(0));
    }

    static class OpenNlpLinguisticsSubclass extends OpenNlpLinguistics {

        @Override
        public Tokenizer getTokenizer() {
            return new ADifferentTokenizer();
        }

    }

    static class ADifferentTokenizer implements Tokenizer {

        @SuppressWarnings("deprecation")
        public Iterable<Token> tokenize(String input, Language language, StemMode stemMode, boolean removeAccents) {
            var token = new SimpleToken("the only token", "the only token");
            token.setType(TokenType.ALPHABETIC);
            return List.of(token);
        }

    }

}
