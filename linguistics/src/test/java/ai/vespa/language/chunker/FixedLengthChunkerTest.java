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
        assertChunks("first second", "first second");
        assertChunks("first. second", "first.", " second");
        assertChunks("first. second.", "first.", " second.");
        assertChunks("firstsecond. third....", "firstsecond.", " third.", "...");
    }

    @Test
    public void testChunkerOnSurrogatePairs() {
        String s = "\uD800\uDC00"; // A surrogate pair representing a code point in the "letter" class
        assertChunks(s + s + s + s + s, s + s + s + s + s);
        assertChunks(s + s + s + s + s + s, s + s + s + s + s + s);
        assertChunks(s + s + s + s + s + s + s, s + s + s + s + s + s + s);
        assertChunks(s + s + s + s + s + s + "." + s, s + s + s + s + s + s + "." + s);
        assertChunks(s + s + s + s + s + s + ". " + s, s + s + s + s + s + s + ".", " " + s);
        assertChunks(s + s + s + s + s + s + "a" + s, s + s + s + s + s + s + "a" + s);
    }

    private void assertChunks(String text, String ... expectedChunks) {
        var tester = new ChunkerTester(new FixedLengthChunker());
        tester.assertChunks(text, List.of("6"), expectedChunks);
        assertEquals(1, tester.cache.size());
    }

}
