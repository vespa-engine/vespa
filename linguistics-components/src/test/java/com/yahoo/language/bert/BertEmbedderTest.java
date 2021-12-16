// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.language.bert;

import com.yahoo.config.FileReference;
import com.yahoo.language.Language;
import com.yahoo.language.process.Embedder;
import com.yahoo.language.simple.SimpleLinguistics;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorAddress;
import org.junit.Test;

import java.io.File;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * Tests the BERT embedder
 *
 * @author bratseth
 */
public class BertEmbedderTest {

    private static final String vocabulary = "src/test/models/bert/bert-base-uncased-vocab.txt";

    @Test
    public void testBertEmbedder() {
        var embedder = new BertEmbedder.Builder().addDefaultModel(new File(vocabulary).toPath()).build();
        var expectedTokenIds = List.of(2054, 2001, 1996, 4254, 1997, 1996, 7128, 2622);
        assertEquals(expectedTokenIds, embedder.embed("what was the impact of the manhattan project",
                                                      new Embedder.Context("destination")));

        var expectedTokens = List.of("what", "was", "the", "impact", "of", "the", "manhattan", "project");
        assertEquals(expectedTokens, embedder.segment("what was the impact of the manhattan project",
                                                      Language.ENGLISH));

        var expectedDenseTensor = Tensor.from("tensor(x[8]):" + expectedTokenIds);
        assertEquals(expectedDenseTensor, embedder.embed("what was the impact of the manhattan project",
                                                         new Embedder.Context("destination"),
                                                         expectedDenseTensor.type()));
    }

    @Test
    public void testBertEmbedderConfiguration() {
        var config = new BertConfig.Builder().model(new BertConfig.Model.Builder().language("unknown")
                                                                                  .path(new FileReference(vocabulary)))
                                             .build();
        var embedder = new BertEmbedder(config);
        var expectedTokenIds = List.of(2054, 2001, 1996, 4254, 1997, 1996, 7128, 2622);
        assertEquals(expectedTokenIds, embedder.embed("what was the impact of the manhattan project",
                                                      new Embedder.Context("destination")));
    }

}
