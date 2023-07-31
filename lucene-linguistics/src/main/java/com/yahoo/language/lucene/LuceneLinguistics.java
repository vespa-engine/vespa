package com.yahoo.language.lucene;

import com.google.inject.Inject;
import com.yahoo.component.provider.ComponentRegistry;
import com.yahoo.language.Linguistics;
import com.yahoo.language.process.Tokenizer;
import com.yahoo.language.simple.SimpleLinguistics;
import org.apache.lucene.analysis.Analyzer;

import java.util.logging.Logger;

/**
 * Factory of Lucene based linguistics processors.
 * As described in the Linguistics docstring
 * > the tokenizer should typically stem, transform and normalize
 *
 * TODO: docs for all available analysis components.
 *
 * @author dainiusjocas
 */
public class LuceneLinguistics extends SimpleLinguistics {

    private static final Logger log = Logger.getLogger(LuceneLinguistics.class.getName());
    private final LuceneTokenizer tokenizer;
    private final LuceneAnalysisConfig config;

    @Inject
    public LuceneLinguistics(LuceneAnalysisConfig config, ComponentRegistry<Analyzer> analyzers) {
        log.config("Creating LuceneLinguistics with: " + config);
        this.config = config;
        this.tokenizer = new LuceneTokenizer(config, analyzers);
    }

    @Override
    public Tokenizer getTokenizer() { return tokenizer; }

    @Override
    public boolean equals(Linguistics other) {
        // Config actually determines if Linguistics are equal
        return (other instanceof LuceneLinguistics) && config.equals(((LuceneLinguistics) other).config);
    }

}
