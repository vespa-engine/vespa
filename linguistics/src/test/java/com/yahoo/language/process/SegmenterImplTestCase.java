// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.language.process;

import com.yahoo.language.Language;
import com.yahoo.language.simple.SimpleNormalizer;
import com.yahoo.language.simple.SimpleToken;
import com.yahoo.language.simple.SimpleTokenizer;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * @author Simon Thoresen Hult
 */
public class SegmenterImplTestCase {

    private final static Segmenter SEGMENTER = new SegmenterImpl(new SimpleTokenizer(new SimpleNormalizer()));

    @Test
    public void requireThatNonIndexableCharactersAreDelimiters() {
        assertSegments("i've", List.of("i", "ve"));
        assertSegments("foo bar. baz", List.of("foo", "bar", "baz"));
        assertSegments("1,2, 3 4", List.of("1", "2", "3", "4"));
    }

    @Test
    public void requireThatAdjacentIndexableTokenTypesAreNotSplit() {
        assertSegments("a1,2b,c3,4d", List.of("a1", "2b", "c3", "4d"));
    }

    @Test
    public void requireThatSegmentationReturnsOriginalForm() {
        assertSegments("a\u030A", List.of("a\u030A"));
        assertSegments("FOO BAR", List.of("FOO", "BAR"));
    }

    @Test
    public void requireThatEmptyInputIsPreserved() {
        assertSegments("", List.of(""));
    }

    private static void assertSegments(String input, List<String> expectedSegments) {
        assertEquals(expectedSegments, SEGMENTER.segment(input, Language.ENGLISH));
    }

    @Test
    public void requireThatEmptyStringsAreSuppressed() {
        Tokenizer fancyTokenizer = new FancyTokenizer();
        Segmenter fancySegmenter = new SegmenterImpl(fancyTokenizer);
        List<String> expectedSegments = List.of("juice", "\u00BD", "oz");
        String input = "juice \u00BD oz";
        assertEquals(expectedSegments, fancySegmenter.segment(input, Language.ENGLISH));
    }

    private static class FancyTokenizer implements Tokenizer {
        private final Tokenizer backend = new SimpleTokenizer(new SimpleNormalizer());

        FancyTokenizer() {}

        public Iterable<Token> tokenize(String input, LinguisticsParameters parameters) {
            List<Token> output = new ArrayList<>();
            for (Token token : backend.tokenize(input,parameters)) {
                if ("\u00BD".equals(token.getOrig())) {
                    // emulate tokenizer turning "1/2" symbol into tree tokens ["1", "/", "2"]
                    Token nt1 = new SimpleToken("").
				setTokenString("1").
				setType(TokenType.NUMERIC).
				setScript(token.getScript()).
				setOffset(token.getOffset());
		    output.add(nt1);
                    Token nt2 = new SimpleToken("").
				setTokenString("\u2044").
				setType(TokenType.SYMBOL).
				setScript(token.getScript()).
				setOffset(token.getOffset());
		    output.add(nt2);
                    Token nt3 = new SimpleToken(token.getOrig()).
				setTokenString("2").
				setType(TokenType.NUMERIC).
				setScript(token.getScript()).
				setOffset(token.getOffset());
		    output.add(nt3);
                } else {
		    output.add(token);
		}
            }
	    return output;
        }
    }
}
