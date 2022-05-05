// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.language.simple;

import com.yahoo.component.annotation.Inject;
import com.yahoo.component.AbstractComponent;
import com.yahoo.language.Linguistics;
import com.yahoo.language.detect.Detector;
import com.yahoo.language.process.CharacterClasses;
import com.yahoo.language.process.GramSplitter;
import com.yahoo.language.process.Normalizer;
import com.yahoo.language.process.Segmenter;
import com.yahoo.language.process.SegmenterImpl;
import com.yahoo.language.process.SpecialTokenRegistry;
import com.yahoo.language.process.Stemmer;
import com.yahoo.language.process.StemmerImpl;
import com.yahoo.language.process.Tokenizer;
import com.yahoo.language.process.Transformer;

import java.util.List;

/**
 * Factory of simple linguistic processor implementations.
 * Useful for testing and english-only use cases.
 *
 * @author bratseth
 * @author bjorncs
 */
public class SimpleLinguistics implements Linguistics {

    // Threadsafe instances
    private final Normalizer normalizer;
    private final Transformer transformer;
    private final Detector detector;
    private final CharacterClasses characterClasses;
    private final GramSplitter gramSplitter;
    private final SpecialTokenRegistry specialTokenRegistry = new SpecialTokenRegistry(List.of());

    @Inject
    public SimpleLinguistics() {
        this.normalizer = new SimpleNormalizer();
        this.transformer = new SimpleTransformer();
        this.detector = new SimpleDetector();
        this.characterClasses = new CharacterClasses();
        this.gramSplitter = new GramSplitter(characterClasses);
    }

    @Override
    public Stemmer getStemmer() { return new StemmerImpl(getTokenizer()); }

    @Override
    public Tokenizer getTokenizer() { return new SimpleTokenizer(normalizer, transformer, specialTokenRegistry); }

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
    public boolean equals(Linguistics other) { return (other instanceof SimpleLinguistics); }

}
