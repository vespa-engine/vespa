// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.language.opennlp;

import com.yahoo.component.annotation.Inject;
import com.yahoo.language.Linguistics;
import com.yahoo.language.detect.Detector;
import com.yahoo.language.process.Tokenizer;
import com.yahoo.language.simple.SimpleLinguistics;

/**
 * Returns a linguistics implementation based on OpenNlp.
 *
 * @author bratseth
 * @author jonmv
 */
public class OpenNlpLinguistics extends SimpleLinguistics {

    private final Detector detector;

    @Inject
    public OpenNlpLinguistics() {
        this.detector = new OpenNlpDetector();
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
