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
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * @author dainiusjocas
 */
class AnalyzerFactory {
    private static final Logger log = Logger.getLogger(AnalyzerFactory.class.getName());

    private final LuceneAnalysisConfig config;

    // Root config directory for all analysis components
    private final Path configDir;

    // Registry of analyzers per language
    // The idea is to create analyzers ONLY WHEN they are needed
    // Analyzers are thread safe so no need to recreate them for every document
    private final Map<String, Analyzer> languageAnalyzers = new HashMap<>();

    private final Analyzer defaultAnalyzer = new StandardAnalyzer();

    private final static String STANDARD_TOKENIZER = "standard";

    private final ComponentRegistry<Analyzer> analyzerComponents;
    private final DefaultAnalyzers defaultAnalyzers;

    public AnalyzerFactory(LuceneAnalysisConfig config, ComponentRegistry<Analyzer> analyzers) {
        this.config = config;
        this.configDir = config.configDir();
        this.analyzerComponents = analyzers;
        this.defaultAnalyzers = DefaultAnalyzers.getInstance();
        log.info("Available in classpath char filters: " + CharFilterFactory.availableCharFilters());
        log.info("Available in classpath tokenizers: " + TokenizerFactory.availableTokenizers());
        log.info("Available in classpath token filters: " + TokenFilterFactory.availableTokenFilters());
    }

    /**
     * Retrieves an analyzer with a given params.
     * Sets up the analyzer if config is provided.
     * Default analyzer is the `StandardAnalyzer`.
     * @param language
     * @param stemMode
     * @param removeAccents
     * @return
     */
    public Analyzer getAnalyzer(Language language, StemMode stemMode, boolean removeAccents) {
        String analyzerKey = generateKey(language, stemMode, removeAccents);

        // If analyzer for language is already known
        if (null != languageAnalyzers.get(analyzerKey)) {
            return languageAnalyzers.get(analyzerKey);
        }
        if (null != config.analysis(analyzerKey)) {
            return setAndReturn(analyzerKey, setUpAnalyzer(analyzerKey));
        }
        if (null != analyzerComponents.getComponent(analyzerKey)) {
            log.info("Analyzer for language=" + analyzerKey + " is from components.");
            return setAndReturn(analyzerKey, analyzerComponents.getComponent(analyzerKey));
        }
        if (null != defaultAnalyzers.get(language)) {
            log.info("Analyzer for language=" + analyzerKey + " is from a list of default language analyzers.");
            return setAndReturn(analyzerKey, defaultAnalyzers.get(language));
        }
        // set the default analyzer for the language
        log.info("StandardAnalyzer is used for language=" + analyzerKey);
        return setAndReturn(analyzerKey, defaultAnalyzer);
    }

    private Analyzer setAndReturn(String analyzerKey, Analyzer analyzer) {
        languageAnalyzers.put(analyzerKey, analyzer);
        return analyzer;
    }

    // TODO: Would it make sense to combine language + stemMode + removeAccents to make
    //  a composite key so we can have more variations possible?
    private String generateKey(Language language, StemMode stemMode, boolean removeAccents) {
        return language.languageCode();
    }

    private Analyzer setUpAnalyzer(String analyzerKey) {
        try {
            LuceneAnalysisConfig.Analysis analysis = config.analysis(analyzerKey);
            log.info("Creating analyzer for: '" + analyzerKey + "' with config: " + analysis);
            CustomAnalyzer.Builder builder = CustomAnalyzer.builder(configDir);
            builder = withTokenizer(builder, analysis);
            builder = addCharFilters(builder, analysis);
            builder = addTokenFilters(builder, analysis);
            return builder.build();
        } catch (Exception e) {
            // Failing to set up the Analyzer, should blow up during testing and VAP should not be deployed.
            // Most likely cause for problems is that a specified resource is not available in VAP.
            // Unit tests should catch such problems and prevent the VAP being deployed.
            log.severe("Failed to build analyzer: '"
                    + analyzerKey
                    + "', with configuration: '"
                    + config.analysis(analyzerKey)
                    + "' with exception: '"
                    + e.getMessage() + "'" );
            throw new RuntimeException(e);
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
        return builder.withTokenizer(tokenizerName, toModifiable(conf));
    }

    private CustomAnalyzer.Builder addCharFilters(CustomAnalyzer.Builder builder,
                                                  LuceneAnalysisConfig.Analysis analysis) throws IOException {
        if (null == analysis) {
            // by default there are no char filters
            return builder;
        }
        for (LuceneAnalysisConfig.Analysis.CharFilters charFilter : analysis.charFilters()) {
            builder.addCharFilter(charFilter.name(), toModifiable(charFilter.conf()));
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
            builder.addTokenFilter(tokenFilter.name(), toModifiable(tokenFilter.conf()));
        }
        return builder;
    }

    /**
     * A config map coming from the Vespa ConfigInstance is immutable while CustomAnalyzer builders
     * mutates the map to mark that a param was consumed. Immutable maps can't be mutated!
     * To overcome this conflict we can wrap the ConfigInstance map in a new HashMap.
     * @param map
     * @return Mutable Map
     */
    private Map<String, String> toModifiable(Map<String, String> map) {
        return new HashMap<>(map);
    }
}
