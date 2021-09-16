// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
//

package com.yahoo.language.sentencepiece;

import com.yahoo.language.Language;

import java.nio.file.Path;

import static org.junit.Assert.assertArrayEquals;

class SentencePieceTester {

    private final SentencePieceEncoder encoder;

    public SentencePieceTester(Path model) {
        this(new SentencePieceEncoder.Builder().addDefaultModel(model));
    }

    public SentencePieceTester(SentencePieceEncoder.Builder builder) {
        this(builder.build());
    }

    public SentencePieceTester(SentencePieceEncoder encoder) {
        this.encoder = encoder;
    }

    public void assertEncoded(String input, Integer... expectedCodes) {
        assertArrayEquals(expectedCodes, encoder.encode(input, Language.UNKNOWN).toArray());
    }

    public void assertSegmented(String input, String... expectedSegments) {
        assertSegmented(Language.UNKNOWN, input, expectedSegments);
    }

    public void assertSegmented(Language language, String input, String... expectedSegments) {
        assertArrayEquals(expectedSegments, encoder.segment(input, language).toArray());
    }

}
