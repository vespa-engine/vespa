// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.language.simple;

import com.yahoo.collections.Tuple2;
import com.yahoo.component.Version;
import com.yahoo.language.Linguistics;
import com.yahoo.language.detect.Detector;
import com.yahoo.language.process.CharacterClasses;
import com.yahoo.language.process.GramSplitter;
import com.yahoo.language.process.Normalizer;
import com.yahoo.language.process.Segmenter;
import com.yahoo.language.process.SegmenterImpl;
import com.yahoo.language.process.Stemmer;
import com.yahoo.language.process.StemmerImpl;
import com.yahoo.language.process.Tokenizer;
import com.yahoo.language.process.Transformer;

/**
 * Factory of pure Java linguistic processor implementations.
 *
 * @author bratseth
 */
public class SimpleLinguistics implements Linguistics {

    // Threadsafe instances
    private final static Normalizer normalizer = new SimpleNormalizer();
    private final static Transformer transformer = new SimpleTransformer();
    private final static Detector detector = new SimpleDetector();
    private final static CharacterClasses characterClasses = new CharacterClasses();
    private final static GramSplitter gramSplitter = new GramSplitter(characterClasses);

    @Override
    public Stemmer getStemmer() { return new StemmerImpl(getTokenizer()); }

    @Override
    public Tokenizer getTokenizer() { return new SimpleTokenizer(normalizer, transformer); }

    @Override
    public Normalizer getNormalizer() { return normalizer; }

    @Override
    public Transformer getTransformer() { return transformer; }

    @Override
    public Segmenter getSegmenter() { return new SegmenterImpl(getTokenizer()); }

    @Override
    public Detector getDetector() { return detector; }

    @Override
    public GramSplitter getGramSplitter() { return gramSplitter; }

    @Override
    public CharacterClasses getCharacterClasses() { return characterClasses; }

    @Override
    public Tuple2<String, Version> getVersion(Component component) {
        return new Tuple2<>("yahoo", new Version(1, 0));
    }

}
