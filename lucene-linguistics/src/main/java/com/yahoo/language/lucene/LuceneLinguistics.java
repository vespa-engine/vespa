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
 */
public class LuceneLinguistics extends SimpleLinguistics {

    private static final Logger log = Logger.getLogger(LuceneLinguistics.class.getName());
    private final Normalizer normalizer;
    private final Transformer transformer;
    private final Tokenizer tokenizer;
    private final Stemmer stemmer;
    private final Segmenter segmenter;
    private final LuceneAnalysisConfig config;

    @Inject
    public LuceneLinguistics(LuceneAnalysisConfig config, ComponentRegistry<Analyzer> analyzers) {
        log.info("Creating LuceneLinguistics with: " + config);
        this.config = config;
        this.tokenizer = new LuceneTokenizer(config, analyzers);
        // NOOP stemmer
        this.stemmer = (word, stemMode, language) -> {
            ArrayList<StemList> stemLists = new ArrayList<>();
            StemList stems = new StemList();
            stems.add(word);
            stemLists.add(stems);
            return stemLists;
        };
        // Segmenter that just wraps a tokenizer
        this.segmenter = (string, language) -> {
            ArrayList<String> segments = new ArrayList<>();
            Iterable<Token> tokens = tokenizer.tokenize(string, language, StemMode.NONE, false);
            tokens.forEach(token -> segments.add(token.getTokenString()));
            return segments;
        };
        // NOOP normalizer
        this.normalizer = (string) -> string;
        // NOOP transformer
        this.transformer = (string, language) -> string;
    }

    @Override
    public Stemmer getStemmer() { return stemmer; }

    @Override
    public Tokenizer getTokenizer() { return tokenizer; }

    @Override
    public Normalizer getNormalizer() { return normalizer; }

    @Override
    public Transformer getTransformer() { return transformer; }

    @Override
    public Segmenter getSegmenter() { return segmenter; }

    public LuceneAnalysisConfig getConfig() {
        return config;
    }

    @Override
    public boolean equals(Linguistics other) {
        return (other instanceof LuceneLinguistics)
                // Config actually determines if Linguistics are equal
                && config.equals(((LuceneLinguistics) other).getConfig()); }
}
