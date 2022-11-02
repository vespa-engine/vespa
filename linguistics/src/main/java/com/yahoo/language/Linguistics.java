// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.language;

import com.yahoo.language.detect.Detector;
import com.yahoo.language.process.CharacterClasses;
import com.yahoo.language.process.GramSplitter;
import com.yahoo.language.process.Normalizer;
import com.yahoo.language.process.Segmenter;
import com.yahoo.language.process.Stemmer;
import com.yahoo.language.process.Tokenizer;
import com.yahoo.language.process.Transformer;

/**
 * <p>Factory of linguistic processors. For technical reasons this provides more flexibility to provide separate
 * components for different operations than is needed in many cases; in particular the tokenizer should typically
 * stem, transform and normalize using the same operations as provided directly by this. A set of adaptors are
 * provided that makes this easy to achieve. Refer to the {com.yahoo.language.simple.SimpleLinguistics} implementation
 * to set this up.</p>
 *
 * <p>Thread safety: Instances of this factory type must be thread safe but the processors
 * returned by the factory methods do not. Clients should request separate processor instances
 * for each thread.</p>
 *
 * @author Mathias Mølster Lidal
 * @author Simon Thoresen Hult
 * @author bratseth
 */
public interface Linguistics {

    enum Component {
        STEMMER,
        TOKENIZER,
        NORMALIZER,
        TRANSFORMER,
        SEGMENTER,
        DETECTOR,
        GRAM_SPLITTER,
        CHARACTER_CLASSES
    }

    /**
     * Returns a thread-unsafe stemmer or lemmatizer.
     * This is used at query time to do stemming of search terms to indexes which contains text tokenized
     * with stemming turned on
     */
    Stemmer getStemmer();

    /**
     * Returns a thread-unsafe tokenizer.
     * This is used at indexing time to produce an optionally stemmed and
     * transformed (accent normalized) stream of indexable tokens.
     */
    Tokenizer getTokenizer();

    /** Returns a thread-unsafe normalizer. This is used at query time to cjk normalize query text. */
    Normalizer getNormalizer();

    /**
     * Returns a thread-unsafe transformer.
     * This is used at query time to do stemming of search terms to indexes which contains text tokenized
     * with accent normalization turned on
     */
    Transformer getTransformer();

    /**
     * Returns a thread-unsafe segmenter.
     * This is used at query time to find the individual semantic components of search terms to indexes
     * tokenized with segmentation.
     */
    Segmenter getSegmenter();

    /**
     * Returns a thread-unsafe detector.
     * The language of the text is a parameter to other linguistic operations.
     * This is used to determine the language of a query or document field when not specified explicitly.
     */
    Detector getDetector();

    /**
     * Returns a thread-unsafe gram splitter.
     * This is used to split query or document text into fixed-length grams which allows matching without needing
     * or using segmented tokens.
     */
    GramSplitter getGramSplitter();

    /** Returns a thread-unsafe character classes instance. */
    CharacterClasses getCharacterClasses();

    /** Check if another instance is equivalent to this one */
    boolean equals(Linguistics other);

}
