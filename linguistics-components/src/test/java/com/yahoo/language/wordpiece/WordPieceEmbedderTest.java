// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.language.wordpiece;

import com.yahoo.config.FileReference;
import com.yahoo.language.tools.EmbedderTester;
import org.junit.Test;

/**
 * Tests the WordPiece embedder
 *
 * @author bratseth
 */
public class WordPieceEmbedderTest {

    private static final String vocabulary = "src/test/models/wordpiece/bert-base-uncased-vocab.txt";

    @Test
    public void testWordPieceSegmentation() {
        var tester = new EmbedderTester(new WordPieceEmbedder.Builder(vocabulary).build());
        tester.assertSegmented("what was the impact of the manhattan project",
                               "what", "was", "the", "impact", "of", "the", "manhattan", "project");
        tester.assertSegmented("overcommunication", "over", "##com", "##mun", "##ication");
    }

    @Test
    public void testWordPieceEmbedding() {
        var tester = new EmbedderTester(new WordPieceEmbedder.Builder(vocabulary).build());
        tester.assertEmbedded("what was the impact of the manhattan project",
                              "tensor(x[8])",
                              2054, 2001, 1996, 4254, 1997, 1996, 7128, 2622);
    }

    @Test
    public void testWordPieceEmbedderConfiguration() {
        var config = new WordPieceConfig.Builder().model(new WordPieceConfig.Model.Builder()
                                                                                  .language("unknown")
                                                                                  .path(new FileReference(vocabulary)))
                                             .build();
        var tester = new EmbedderTester(new WordPieceEmbedder(config));
        tester.assertSegmented("what was the impact of the manhattan project",
                               "what", "was", "the", "impact", "of", "the", "manhattan", "project");
    }

}
