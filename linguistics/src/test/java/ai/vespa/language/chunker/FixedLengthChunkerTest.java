// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.language.chunker;

import com.yahoo.language.process.Chunker;
import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

/**
 * @author bratseth
 */
public class FixedLengthChunkerTest {

    @Test
    public void testChunker() {
        assertChunks("");
        assertChunks("first", "first");
        assertChunks("first ", "first ");
        assertChunks("first second", "first ", "second");
        assertChunks("first. second", "first.", " second");
        assertChunks("first. second.", "first.", " second", ".");
        assertChunks("firstsecond. third....", "firstse", "cond. ", "third.", "...");
    }

    @Test
    public void testChunkerBoundaries() {
        // With target length = 20, soft max length is 21, and hard max length 22

        // -> First chunk is 20 long
        assertChunks(20, "first sentence, end. Next sentence.",
                     "first sentence, end.", " Next sentence.");
        // -> First chunk is 21 long as we get to a soft boundary
        assertChunks(20, "first sentence, stop.Next sentence.",
                     "first sentence, stop.", "Next sentence.");
        // -> First chunk is 22 long, as there is no boundary before reaching the hard limit
        assertChunks(20, "first sentence, endNext sentence.",
                     "first sentence, endNex", "t sentence.");
    }

    @Test
    public void testChunkerOnSurrogatePairs() {
        String s = "\uD800\uDC00"; // A surrogate pair representing a code point in the "letter" class
        assertChunks(s + s + s + s + s, s + s + s + s + s);
        assertChunks(s + s + s + s + s + s, s + s + s + s + s + s);
        assertChunks(s + s + s + s + s + s + s, s + s + s + s + s + s + s);
        assertChunks(s + s + s + s + s + s + "." + s, s + s + s + s + s + s + ".", s);
        assertChunks(s + s + s + s + s + s + ". " + s, s + s + s + s + s + s + ".", " " + s);
        assertChunks(s + s + s + s + s + s + "a" + s, s + s + s + s + s + s + "a", s);
    }

    private void assertChunks(String text, String ... expectedChunks) {
        assertChunks(6, text, expectedChunks);
    }

    private void assertChunks(int length, String text, String ... expectedChunks) {
        var tester = new ChunkerTester(new FixedLengthChunker());
        tester.assertChunks(text, List.of(String.valueOf(length)), expectedChunks);
        assertEquals(1, tester.cache.size());
    }

}
