// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.language.chunker;

import com.yahoo.language.process.Chunker;
import org.junit.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

/**
 * @author bratseth
 */
public class SentenceChunkerTest {

    @Test
    public void testChunker() {
        assertChunks("");
        assertChunks("This is ONE sentence.",
                     "This is ONE sentence.");
        assertChunks("One without punctuation",
                     "One without punctuation");
        assertChunks("Questions? Yes, also sentences!",
                     "Questions?", " Yes, also sentences!");
        assertChunks("This: Is one sentence .",
                     "This: Is one sentence .");
        assertChunks("Sentence with ellipsis ... And another...!!",
                     "Sentence with ellipsis ...", " And another...!!");
        assertChunks(",.-2Noise---.-.-..foo   ",
                     ",.-2Noise---.", "-.-..foo   ");
        assertChunks("!!!...?",
                     "!!!...?");
        assertChunks("!!!. ..?",
                     "!!!. ..?");
    }

    @Test
    public void testChunkerOnSurrogatePairs() {
        String s = "\uD800\uDC00"; // A surrogate pair representing a code point in the "letter" class
        assertChunks(s + s + "." + s + "A" + s,
                     s + s + ".", s + "A" + s);
    }

    private void assertChunks(String text, String ... expectedChunks) {
        var tester = new ChunkerTester(new SentenceChunker());
        tester.assertChunks(text, expectedChunks);
    }

}
