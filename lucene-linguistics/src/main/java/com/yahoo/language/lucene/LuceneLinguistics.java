package com.yahoo.language.lucene;

import com.google.inject.Inject;
import com.yahoo.component.provider.ComponentRegistry;
import com.yahoo.language.Linguistics;
import com.yahoo.language.process.*;
import com.yahoo.language.simple.SimpleLinguistics;
import org.apache.lucene.analysis.Analyzer;

import java.util.ArrayList;
import java.util.logging.Logger;

/**
 * Factory of Lucene based linguistics processor.
 * As described in the Linguistics docstring
 * > the tokenizer should typically stem, transform and normalize
 * The Stemmer, Transformer, Normalizer, and Segmenter implementations are mostly NOOP.
 *
 * TODO: docs for all available analysis components.
 * TODO: some registry for available language Analyzers.
 *
 * @author dainiusjocas
 */
public class LuceneLinguistics extends SimpleLinguistics {

    private static final Logger log = Logger.getLogger(LuceneLinguistics.class.getName());
    private final Tokenizer tokenizer;
    private final LuceneAnalysisConfig config;

    @Inject
    public LuceneLinguistics(LuceneAnalysisConfig config, ComponentRegistry<Analyzer> analyzers) {
        log.info("Creating LuceneLinguistics with: " + config);
        this.config = config;
        this.tokenizer = new LuceneTokenizer(config, analyzers);
    }

    @Override
    public Tokenizer getTokenizer() { return tokenizer; }

    @Override
    public boolean equals(Linguistics other) {
        return (other instanceof LuceneLinguistics)
                // Config actually determines if Linguistics are equal
                && config.equals(((LuceneLinguistics) other).config); }

}
