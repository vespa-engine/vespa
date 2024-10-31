// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.language.opennlp;

import ai.vespa.opennlp.OpenNlpConfig;
import com.yahoo.component.annotation.Inject;
import com.yahoo.language.Linguistics;
import com.yahoo.language.detect.Detector;
import com.yahoo.language.process.Segmenter;
import com.yahoo.language.process.SegmenterImpl;
import com.yahoo.language.process.Stemmer;
import com.yahoo.language.process.StemmerImpl;
import com.yahoo.language.process.Tokenizer;
import com.yahoo.language.simple.SimpleLinguistics;
import static com.yahoo.language.opennlp.OpenNlpTokenizer.Mode.*;

/**
 * A linguistics implementation based on OpenNlp.
 *
 * @author bratseth
 * @author jonmv
 */
public class OpenNlpLinguistics extends SimpleLinguistics {

    private final boolean snowballStemmingForEnglish;
    private final boolean cjk;
    private final boolean createCjkGrams;
    private final Detector detector;

    public OpenNlpLinguistics() {
        this(new OpenNlpConfig.Builder().build());
    }

    @Inject
    public OpenNlpLinguistics(OpenNlpConfig config) {
        this.snowballStemmingForEnglish = config.snowballStemmingForEnglish();
        this.cjk = config.cjk();
        this.createCjkGrams = config.createCjkGrams();
        this.detector = new OpenNlpDetector();
    }

    @Override
    public Tokenizer getTokenizer() {
        return new OpenNlpTokenizer(OpenNlpTokenizer.Mode.index, getNormalizer(), getTransformer(),
                                    snowballStemmingForEnglish, cjk, createCjkGrams);
    }

    @Override
    public Stemmer getStemmer() {
        return new StemmerImpl(forQuerying(getTokenizer()));
    }

    @Override
    public Segmenter getSegmenter() {
        return new SegmenterImpl(forQuerying(getTokenizer()));
    }

    @Override
    public Detector getDetector() { return detector; }

    @Override
    public boolean equals(Linguistics other) { return (other instanceof OpenNlpLinguistics); }

    private Tokenizer forQuerying(Tokenizer tokenizer) {
        if ( ! (tokenizer.getClass() == OpenNlpTokenizer.class)) // this has been subclassed and partially overridden
            return tokenizer;
        return ((OpenNlpTokenizer)tokenizer).withMode(query);
    }

}
