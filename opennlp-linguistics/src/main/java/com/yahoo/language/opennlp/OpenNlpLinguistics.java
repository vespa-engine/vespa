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

    private final boolean cjk;
    private final boolean createCjkGrams;
    private final Detector detector;

    public OpenNlpLinguistics() {
        this(new OpenNlpConfig.Builder().build());
    }

    @Inject
    public OpenNlpLinguistics(OpenNlpConfig config) {
        this.cjk = config.cjk();
        this.createCjkGrams = config.createCjkGrams();
        this.detector = new OpenNlpDetector();
    }

    @Override
    public Stemmer getStemmer() { return new StemmerImpl(createTokenizer().withMode(query)); }

    @Override
    public Tokenizer getTokenizer() { return createTokenizer(); }

    @Override
    public Segmenter getSegmenter() { return new SegmenterImpl(createTokenizer().withMode(query)); }

    @Override
    public Detector getDetector() { return detector; }

    @Override
    public boolean equals(Linguistics other) { return (other instanceof OpenNlpLinguistics); }

    private OpenNlpTokenizer createTokenizer() {
        return new OpenNlpTokenizer(OpenNlpTokenizer.Mode.index, getNormalizer(), getTransformer(), cjk, createCjkGrams);
    }

}
