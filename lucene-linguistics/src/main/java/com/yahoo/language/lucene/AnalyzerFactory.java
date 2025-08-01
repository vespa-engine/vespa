// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.language.lucene;

import com.yahoo.component.provider.ComponentRegistry;
import com.yahoo.language.Language;
import com.yahoo.language.process.StemMode;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.CharFilterFactory;
import org.apache.lucene.analysis.TokenFilterFactory;
import org.apache.lucene.analysis.TokenizerFactory;
import org.apache.lucene.analysis.custom.CustomAnalyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.logging.Logger;

/**
 * Analyzers for various languages.
 *
 * @author dainiusjocas
 */
class AnalyzerFactory {

    private static final Logger log = Logger.getLogger(AnalyzerFactory.class.getName());

    private final LuceneAnalysisConfig config;

    // Registry of analyzers per language
    // The idea is to create analyzers ONLY WHEN they are needed
    // Analyzers are thread safe so no need to recreate them for every document
    private final Map<AnalyzerKey, Analyzer> languageAnalyzers = new ConcurrentHashMap<>();

    private final Analyzer defaultAnalyzer = new StandardAnalyzer();

    private final static String STANDARD_TOKENIZER = "standard";

    private final ComponentRegistry<Analyzer> analyzerComponents;
    private final DefaultAnalyzers defaultAnalyzers;

    private static String dump(ComponentRegistry<Analyzer> analyzers) {
        StringBuilder buf = new StringBuilder();
        buf.append("[");
        var map = analyzers.allComponentsById();
        for (var entry : map.entrySet()) {
            buf.append(" {");
            buf.append(entry.getKey().toString());
            buf.append(":");
            buf.append(entry.getValue().getClass());
            buf.append("}");
        }
        buf.append(" ]");
        return buf.toString();
    }

    public AnalyzerFactory(LuceneAnalysisConfig config, ComponentRegistry<Analyzer> analyzers) {
        this.config = config;
        this.analyzerComponents = analyzers;
        this.defaultAnalyzers = new DefaultAnalyzers();
        log.config("Available in classpath char filters: " + CharFilterFactory.availableCharFilters());
        log.config("Available in classpath tokenizers: " + TokenizerFactory.availableTokenizers());
        log.config("Available in classpath token filters: " + TokenFilterFactory.availableTokenFilters());
        log.config("Available in component registry: " + dump(analyzers));
    }

    /**
     * Retrieves an analyzer with a given params.
     * Sets up the analyzer if config is provided.
     * Default analyzer is the `StandardAnalyzer`.
     */
    public Analyzer getAnalyzer(Language language, StemMode stemMode, boolean removeAccents) {
        return languageAnalyzers.computeIfAbsent(new AnalyzerKey(language, stemMode, removeAccents),
                                                 this::createAnalyzer);
    }

    private Analyzer createAnalyzer(AnalyzerKey analyzerKey) {
        Analyzer analyzer = analysisConfig(analyzerKey);
        if (analyzer != null) return analyzer;

        analyzer = fromComponents(analyzerKey);
        if (analyzer != null) return analyzer;

        analyzer = defaultAnalyzers.get(analyzerKey.language());
        if (analyzer != null) return analyzer;

        return defaultAnalyzer;
    }

    /**
     * First, checks if more specific (language + stemMode) analysis is configured.
     * Second, checks if analysis is configured only for a languageCode.
     */
    private Analyzer analysisConfig(AnalyzerKey analyzerKey) {
        return lookup(analyzerKey, key -> createAnalyzer(analyzerKey, config.analysis(key)));
    }

    /**
     * First, checks if a component is configured for a languageCode + StemMode.
     * Second, checks if Analyzer is configured only for a languageCode.
     */
    private Analyzer fromComponents(AnalyzerKey analyzerKey) {
        return lookup(analyzerKey, key -> analyzerComponents.getComponent(key));
    }

    private Analyzer lookup(AnalyzerKey key, Function<String, Analyzer> analyzerMap) {
        var analyzer = analyzerMap.apply(key.languageCodeAndStemMode());
        if (analyzer != null) return analyzer;

        analyzer = analyzerMap.apply(key.generalizedLanguageCodeAndStemMode());
        if (analyzer != null) return analyzer;

        analyzer = analyzerMap.apply(key.languageCode());
        if (analyzer != null) return analyzer;

        analyzer = analyzerMap.apply(key.generalizedLanguageCode());
        return analyzer;
    }

    private Analyzer createAnalyzer(AnalyzerKey analyzerKey, LuceneAnalysisConfig.Analysis analysis) {
        try {
            if (analysis == null) return null;
            CustomAnalyzer.Builder builder = config.configDir()
                    // Root config directory for all analysis components in the application package
                    .map(CustomAnalyzer::builder)
                    // else load resource files from the classpath
                    .orElseGet(CustomAnalyzer::builder);
            builder = withTokenizer(builder, analysis);
            builder = addCharFilters(builder, analysis);
            builder = addTokenFilters(builder, analysis);
            return builder.build();
        } catch (Exception e) {
            // Failing to set up the Analyzer, should blow up during testing and VAP should not be deployed.
            // Most likely cause for problems is that a specified resource is not available in VAP.
            // Unit tests should catch such problems and prevent the VAP being deployed.
            throw new RuntimeException("Failed to build analyzer " + analyzerKey +
                                       ", with configuration " + analysis, e);
        }
    }

    private CustomAnalyzer.Builder withTokenizer(CustomAnalyzer.Builder builder,
                                                 LuceneAnalysisConfig.Analysis analysis) throws IOException {
        if (null == analysis) {
            // By default we use the "standard" tokenizer
            return builder.withTokenizer(STANDARD_TOKENIZER, new HashMap<>());
        }
        String tokenizerName = analysis.tokenizer().name();
        Map<String, String> conf = analysis.tokenizer().conf();
        return builder.withTokenizer(tokenizerName, asModifiable(conf));
    }

    private CustomAnalyzer.Builder addCharFilters(CustomAnalyzer.Builder builder,
                                                  LuceneAnalysisConfig.Analysis analysis) throws IOException {
        if (null == analysis) {
            // by default there are no char filters
            return builder;
        }
        for (LuceneAnalysisConfig.Analysis.CharFilters charFilter : analysis.charFilters()) {
            builder.addCharFilter(charFilter.name(), asModifiable(charFilter.conf()));
        }
        return builder;
    }

    private CustomAnalyzer.Builder addTokenFilters(CustomAnalyzer.Builder builder,
                                                   LuceneAnalysisConfig.Analysis analysis) throws IOException {
        if (null == analysis) {
            // by default no token filters are added
            return builder;
        }
        for (LuceneAnalysisConfig.Analysis.TokenFilters tokenFilter : analysis.tokenFilters()) {
            builder.addTokenFilter(tokenFilter.name(), asModifiable(tokenFilter.conf()));
        }
        return builder;
    }

    /**
     * A config map coming from the Vespa ConfigInstance is immutable while CustomAnalyzer builders
     * mutates the map to mark that a param was consumed. Immutable maps can't be mutated!
     * To overcome this conflict we can wrap the ConfigInstance map in a new HashMap.
     */
    private Map<String, String> asModifiable(Map<String, String> map) {
        return new HashMap<>(map);
    }

    private record AnalyzerKey(Language language, StemMode stemMode, boolean removeAccents) {

        /**
         * Combines the languageCode and the stemMode.
         * It allows to specify up to 6 (5 StemModes and only language code) analyzers per language.
         * The `/` is used so that it doesn't conflict with ComponentRegistry keys.
         */
        public String languageCodeAndStemMode() {
            return languageCode() + "/" + stemMode.toString();
        }

        public String generalizedLanguageCodeAndStemMode() {
            return generalizedLanguageCode() + "/" + stemMode.toString();
        }

        public String languageCode() {
            return language.languageCode();
        }

        public String generalizedLanguageCode() {
            if (language == Language.CHINESE_SIMPLIFIED || language == Language.CHINESE_TRADITIONAL)
                return "zh";
            return language.languageCode();
        }

        @Override
        public boolean equals(Object o) {
            if (o == this) return true;
            if ( ! (o instanceof AnalyzerKey other)) return false;
            return other.language == this.language && other.stemMode == this.stemMode;
        }

        @Override
        public int hashCode() {
            return Objects.hash(language, stemMode);
        }

    }

}
