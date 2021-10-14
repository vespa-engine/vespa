// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
//

package com.yahoo.language.sentencepiece;

import com.yahoo.language.Language;
import com.yahoo.language.process.Embedder;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;

import java.nio.file.Path;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

class SentencePieceTester {

    private final SentencePieceEmbedder embedder;

    public SentencePieceTester(Path model) {
        this(new SentencePieceEmbedder.Builder().addDefaultModel(model));
    }

    public SentencePieceTester(SentencePieceEmbedder.Builder builder) {
        this(builder.build());
    }

    public SentencePieceTester(SentencePieceEmbedder embedder) {
        this.embedder = embedder;
    }

    public void assertEmbedded(String input, Integer... expectedCodes) {
        assertArrayEquals(expectedCodes, embedder.embed(input, new Embedder.Context("test")).toArray());
    }

    public void assertEmbedded(String input, String tensorType, String tensor) {
        TensorType type = TensorType.fromSpec(tensorType);
        Tensor expected = Tensor.from(type, tensor);
        assertEquals(expected, embedder.embed(input, new Embedder.Context("test"), type));
    }

    public void assertSegmented(String input, String... expectedSegments) {
        assertSegmented(Language.UNKNOWN, input, expectedSegments);
    }

    public void assertSegmented(Language language, String input, String... expectedSegments) {
        assertArrayEquals(expectedSegments, embedder.segment(input, language).toArray());
    }

}
