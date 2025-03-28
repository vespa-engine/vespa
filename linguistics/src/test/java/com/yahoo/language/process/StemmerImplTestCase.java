// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.language.process;

import com.yahoo.language.Language;
import com.yahoo.language.simple.SimpleNormalizer;
import com.yahoo.language.simple.SimpleToken;
import com.yahoo.language.simple.SimpleTokenizer;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.ArrayList;

import static org.junit.Assert.assertEquals;

/**
 * @author Simon Thoresen Hult
 */
public class StemmerImplTestCase {

    @Test
    public void requireThatStemIsNormalizedAndLowerCased() {
        assertStem("FOO", List.of("foo"), true);
        assertStem("a\u030A", List.of("\u00E5"), false);
    }

    @Test
    public void requireThatOnlyIndexableTokensAreReturned() {
        assertStem("foo. (bar)!", List.of("foo", "bar"), true);
    }

    @Test
    public void requireThatSpecialTokensAreNotDecompounded() {
        SimpleToken token = new SimpleToken("c++").setType(TokenType.ALPHABETIC)
                                                  .setTokenString("c++")
                                                  .addComponent(new SimpleToken("c").setType(TokenType.ALPHABETIC)
                                                                                    .setTokenString("c"))
                                                  .addComponent(new SimpleToken("p").setType(TokenType.ALPHABETIC)
                                                                                    .setTokenString("p"))
                                                  .addComponent(new SimpleToken("p").setType(TokenType.ALPHABETIC)
                                                                                    .setTokenString("p"));
        Tokenizer tokenizer = Mockito.mock(Tokenizer.class);
        Mockito.when(tokenizer.tokenize(Mockito.anyString(), Mockito.<LinguisticsParameters>any()))
               .thenReturn(List.of(token));
        Stemmer stemmer = new StemmerImpl(tokenizer);

        token.setSpecialToken(false);
        assertEquals(List.of(new StemList("c"),
                                   new StemList("p"),
                                   new StemList("p")),
                     stemmer.stem("c++",
                                  new LinguisticsParameters(Language.ENGLISH, StemMode.SHORTEST, true, true)));

        token.setSpecialToken(true);
        assertEquals(List.of(new StemList("c++")),
                     stemmer.stem("c++", new LinguisticsParameters(Language.ENGLISH, StemMode.SHORTEST, true, true)));
    }

    private static void assertStem(String input, List<String> expectedStems, boolean removeAccents) {
        Stemmer stemmer = new StemmerImpl(new SimpleTokenizer(new SimpleNormalizer()));
        var parameters = new LinguisticsParameters(Language.ENGLISH, StemMode.ALL, removeAccents, true);
        List<String> got = new ArrayList<>();
        for (StemList word : stemmer.stem(input, parameters)) {
            got.add(word.get(0));
        }
        assertEquals(expectedStems, got);
    }
}
