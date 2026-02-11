// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query.parser;

import com.yahoo.collections.Tuple2;
import com.yahoo.component.Version;
import com.yahoo.language.Linguistics;
import com.yahoo.language.detect.Detector;
import com.yahoo.language.process.*;
import com.yahoo.language.simple.SimpleLinguistics;

/**
 * @author Simon Thoresen Hult
 */
public class TestLinguistics implements Linguistics {

    public static final Linguistics INSTANCE = new TestLinguistics();
    private final Linguistics linguistics = new SimpleLinguistics();

    private TestLinguistics() {
        // hide
    }

    @Override
    public Stemmer getStemmer() {
        return linguistics.getStemmer();
    }

    @Override
    public com.yahoo.language.process.Tokenizer getTokenizer() {
        return linguistics.getTokenizer();
    }

    @Override
    public Normalizer getNormalizer() {
        return linguistics.getNormalizer();
    }

    @Override
    public Transformer getTransformer() {
        return linguistics.getTransformer();
    }

    @Override
    public Segmenter getSegmenter() {
        return new TestSegmenter();
    }

    @Override
    public Detector getDetector() {
        return linguistics.getDetector();
    }

    @Override
    public GramSplitter getGramSplitter() {
        return linguistics.getGramSplitter();
    }

    @Override
    public CharacterClasses getCharacterClasses() {
        return linguistics.getCharacterClasses();
    }

    public boolean equals(Linguistics other) {
        return (other instanceof TestLinguistics);
    }

}


