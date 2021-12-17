// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.language.tools;

import com.yahoo.language.Language;
import com.yahoo.language.process.Embedder;
import com.yahoo.language.process.Segmenter;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tester of embedders.
 *
 * @author bratseth
 */
public class EmbedderTester {

    private final Embedder embedder;

    public EmbedderTester(Embedder embedder) {
        this.embedder = embedder;
    }

    /**
     * Tests both embedding to a list of id's and encoding the same ids to a vector of the given type.
     *
     * @param expectedCodes all the expected codes of the given input, not including any trailing 0-paddings
     *                      required for the tensor only
     */
    public void assertEmbedded(String input, String tensorType, Integer... expectedCodes) {
        TensorType type = TensorType.fromSpec(tensorType);
        assertEquals(1, type.dimensions().size());
        assertTrue(type.dimensions().get(0).isIndexed());

        int tensorSize = type.dimensions().get(0).size().get().intValue();

        assertArrayEquals(expectedCodes, embedder.embed(input, new Embedder.Context("test")).toArray());

        var builder = Tensor.Builder.of(type);
        for (int i = 0; i < tensorSize; i++)
            builder.cell(i < expectedCodes.length ? expectedCodes[i] : 0, i);
        assertEquals(builder.build(), embedder.embed(input, new Embedder.Context("destination"), type));
    }

    public void assertSegmented(String input, String... expectedSegments) {
        assertSegmented(Language.UNKNOWN, input, expectedSegments);
    }

    public void assertSegmented(Language language, String input, String... expectedSegments) {
        List<String> segments = ((Segmenter)embedder).segment(input, language);
        assertArrayEquals("Actual segments: " + segments,
                          expectedSegments, ((Segmenter)embedder).segment(input, language).toArray());
    }

}
