// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.language.process;

import com.yahoo.language.Language;
import com.yahoo.language.simple.SimpleNormalizer;
import com.yahoo.language.simple.SimpleToken;
import com.yahoo.language.simple.SimpleTokenizer;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;

import static org.junit.Assert.assertEquals;

/**
 * @author Simon Thoresen Hult
 */
public class StemmerImplTestCase {

    @Test
    public void requireThatStemIsNormalizedAndLowerCased() {
        assertStem("FOO", Arrays.asList("foo"));
        assertStem("a\u030A", Arrays.asList("\u00E5"));
    }

    @Test
    public void requireThatOnlyIndexableTokensAreReturned() {
        assertStem("foo. (bar)!", Arrays.asList("foo", "bar"));
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
        Mockito.when(tokenizer.tokenize(Mockito.anyString(), Mockito.<Language>any(), Mockito.<StemMode>any(),
                                        Mockito.anyBoolean()))
               .thenReturn(Arrays.<Token>asList(token));
        Stemmer stemmer = new StemmerImpl(tokenizer);

        token.setSpecialToken(false);
        assertEquals(Arrays.asList(new StemList("c"),
                                   new StemList("p"),
                                   new StemList("p")),
                     stemmer.stem("c++", StemMode.SHORTEST, Language.ENGLISH));

        token.setSpecialToken(true);
        assertEquals(Arrays.asList(new StemList("c++")),
                     stemmer.stem("c++", StemMode.SHORTEST, Language.ENGLISH));
    }

    private static void assertStem(String input, List<String> expectedStems) {
        Stemmer stemmer = new StemmerImpl(new SimpleTokenizer(new SimpleNormalizer()));
        List<String> got = new ArrayList<>();
        for (StemList word : stemmer.stem(input, StemMode.ALL, Language.ENGLISH)) {
            got.add(word.get(0));
        }
        assertEquals(expectedStems, got);
    }
}
