// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.language.opennlp;

import com.google.inject.Inject;
import com.yahoo.language.Linguistics;
import com.yahoo.language.detect.Detector;
import com.yahoo.language.process.Tokenizer;
import com.yahoo.language.simple.SimpleDetector;
import com.yahoo.language.simple.SimpleLinguistics;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Returns a linguistics implementation based on OpenNlp,
 * and (optionally, default on) Optimaize for language detection.
 *
 * @author bratseth
 */
public class OpenNlpLinguistics extends SimpleLinguistics {

    private static final Logger log = Logger.getLogger(OpenNlpLinguistics.class.getName());
    private final Detector detector;

    public OpenNlpLinguistics() {
        this(true);
    }

    @Inject
    public OpenNlpLinguistics(OpennlpLinguisticsConfig config) {
        this(config.detector().enableOptimaize());
    }

    public OpenNlpLinguistics(boolean enableOptimaize) {
        this(enableOptimaize ? new OptimaizeDetector() : new SimpleDetector());
        log.log(Level.FINE, "using "+(enableOptimaize ? "Optimaize" : "Simple")+" detector");
    }

    private OpenNlpLinguistics(Detector detector) {
        this.detector = detector;
    }

    @Override
    public Tokenizer getTokenizer() {
        return new OpenNlpTokenizer(getNormalizer(), getTransformer());
    }

    @Override
    public Detector getDetector() { return detector; }

    @Override
    public boolean equals(Linguistics other) { return (other instanceof OpenNlpLinguistics); }

}
