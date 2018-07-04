// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.language.process;

import com.yahoo.language.Language;
import com.yahoo.language.simple.SimpleNormalizer;
import com.yahoo.language.simple.SimpleTokenizer;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * @author Simon Thoresen Hult
 */
public class SegmenterImplTestCase {

    private final static Segmenter SEGMENTER = new SegmenterImpl(new SimpleTokenizer(new SimpleNormalizer()));

    @Test
    public void requireThatNonIndexableCharactersAreDelimiters() {
        assertSegments("i've", Arrays.asList("i", "ve"));
        assertSegments("foo bar. baz", Arrays.asList("foo", "bar", "baz"));
        assertSegments("1,2, 3 4", Arrays.asList("1", "2", "3", "4"));
    }

    @Test
    public void requireThatAdjacentIndexableTokenTypesAreNotSplit() {
        assertSegments("a1,2b,c3,4d", Arrays.asList("a1", "2b", "c3", "4d"));
    }

    @Test
    public void requireThatSegmentationReturnsOriginalForm() {
        assertSegments("a\u030A", Arrays.asList("a\u030A"));
        assertSegments("FOO BAR", Arrays.asList("FOO", "BAR"));
    }

    private static void assertSegments(String input, List<String> expectedSegments) {
        assertEquals(expectedSegments, SEGMENTER.segment(input, Language.ENGLISH));
    }

}
