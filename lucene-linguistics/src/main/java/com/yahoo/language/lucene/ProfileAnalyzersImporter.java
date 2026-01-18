// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.language.lucene;

import com.yahoo.component.provider.ComponentRegistry;
import com.yahoo.language.Language;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.ar.ArabicAnalyzer;
import org.apache.lucene.analysis.bg.BulgarianAnalyzer;
import org.apache.lucene.analysis.bn.BengaliAnalyzer;
import org.apache.lucene.analysis.ca.CatalanAnalyzer;
import org.apache.lucene.analysis.cjk.CJKAnalyzer;
import org.apache.lucene.analysis.ckb.SoraniAnalyzer;
import org.apache.lucene.analysis.custom.CustomAnalyzer;
import org.apache.lucene.analysis.cz.CzechAnalyzer;
import org.apache.lucene.analysis.da.DanishAnalyzer;
import org.apache.lucene.analysis.de.GermanAnalyzer;
import org.apache.lucene.analysis.el.GreekAnalyzer;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.es.SpanishAnalyzer;
import org.apache.lucene.analysis.et.EstonianAnalyzer;
import org.apache.lucene.analysis.eu.BasqueAnalyzer;
import org.apache.lucene.analysis.fa.PersianAnalyzer;
import org.apache.lucene.analysis.fi.FinnishAnalyzer;
import org.apache.lucene.analysis.fr.FrenchAnalyzer;
import org.apache.lucene.analysis.ga.IrishAnalyzer;
import org.apache.lucene.analysis.gl.GalicianAnalyzer;
import org.apache.lucene.analysis.hi.HindiAnalyzer;
import org.apache.lucene.analysis.hu.HungarianAnalyzer;
import org.apache.lucene.analysis.hy.ArmenianAnalyzer;
import org.apache.lucene.analysis.id.IndonesianAnalyzer;
import org.apache.lucene.analysis.it.ItalianAnalyzer;
import org.apache.lucene.analysis.lt.LithuanianAnalyzer;
import org.apache.lucene.analysis.lv.LatvianAnalyzer;
import org.apache.lucene.analysis.ne.NepaliAnalyzer;
import org.apache.lucene.analysis.nl.DutchAnalyzer;
import org.apache.lucene.analysis.no.NorwegianAnalyzer;
import org.apache.lucene.analysis.pt.PortugueseAnalyzer;
import org.apache.lucene.analysis.ro.RomanianAnalyzer;
import org.apache.lucene.analysis.ru.RussianAnalyzer;
import org.apache.lucene.analysis.sr.SerbianAnalyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.sv.SwedishAnalyzer;
import org.apache.lucene.analysis.ta.TamilAnalyzer;
import org.apache.lucene.analysis.te.TeluguAnalyzer;
import org.apache.lucene.analysis.th.ThaiAnalyzer;
import org.apache.lucene.analysis.tr.TurkishAnalyzer;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Creates all available analyzers for a single profile
 *
 * @author dainiusjocas
 * @author bratseth
 */
public class ProfileAnalyzersImporter {

    private final static String STANDARD_TOKENIZER = "standard";

    ProfileAnalyzers importAnalyzers(String profile,
                                     Set<AnalyzerKey> keys,
                                     LuceneAnalysisConfig config,
                                     ComponentRegistry<Analyzer> components) {
        Map<AnalyzerKey, Analyzer> analyzers = new HashMap<>();
        for (var key : keys)
            analyzers.put(key, importAnalyzerFor(key, config, components));
        if (profile == null)
            addProfilelessDefaults(analyzers);
        return new ProfileAnalyzers(analyzers);
    }

    private Analyzer importAnalyzerFor(AnalyzerKey key,
                                       LuceneAnalysisConfig config,
                                       ComponentRegistry<Analyzer> components) {
        Analyzer analyzer = importAnalyzerFor(key, config);
        if (analyzer != null) return analyzer;
        return findAnalyzer(key, components);
    }

    private Analyzer importAnalyzerFor(AnalyzerKey analyzerKey, LuceneAnalysisConfig config) {
        LuceneAnalysisConfig.Analysis analyzerConfig = findAnalyzerConfig(analyzerKey, config);
        if (analyzerConfig == null) return null;
        try {
            CustomAnalyzer.Builder builder = config.configDir()
                                                   // Root config directory for all analysis components in the application package
                                                   .map(CustomAnalyzer::builder)
                                                   // else load resource files from the classpath
                                                   .orElseGet(CustomAnalyzer::builder);
            builder = withTokenizer(builder, analyzerConfig);
            builder = addCharFilters(builder, analyzerConfig);
            builder = addTokenFilters(builder, analyzerConfig);
            return builder.build();
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to build analyzer " + analyzerKey +
                                               ", with configuration " + analyzerConfig, e);
        }
    }

    private LuceneAnalysisConfig.Analysis findAnalyzerConfig(AnalyzerKey analyzerKey, LuceneAnalysisConfig config) {
        for (var analyzerConfig : config.analysis().entrySet()) {
            if (AnalyzerKey.fromString(analyzerConfig.getKey()).equals(analyzerKey))
                return analyzerConfig.getValue();
        }
        return null;
    }

    private Analyzer findAnalyzer(AnalyzerKey analyzerKey, ComponentRegistry<Analyzer> components) {
        for (var analyzer : components.allComponentsById().entrySet()) {
            if (AnalyzerKey.fromString(analyzer.getKey().stringValue()).equals(analyzerKey))
                return analyzer.getValue();
        }
        return null;
    }

    private CustomAnalyzer.Builder withTokenizer(CustomAnalyzer.Builder builder,
                                                 LuceneAnalysisConfig.Analysis analysis) throws IOException {
        if (null == analysis) {
            // By default, we use the "standard" tokenizer
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
     * mutates the map to mark that a param was consumed.
     */
    private Map<String, String> asModifiable(Map<String, String> map) {
        return new HashMap<>(map);
    }

    /**
     * We fall back to looking up without matching profile, and must then hit defaults for unspecified
     * languages, and - finally - the default when there are no matches.
     */
    private void addProfilelessDefaults(Map<AnalyzerKey, Analyzer> analyzers) {
        addDefaultFor(Language.ARABIC, new ArabicAnalyzer(), analyzers);
        addDefaultFor(Language.BULGARIAN, new BulgarianAnalyzer(), analyzers);
        addDefaultFor(Language.BENGALI, new BengaliAnalyzer(), analyzers);
        addDefaultFor(Language.CATALAN, new CatalanAnalyzer(), analyzers);
        if ( ! analyzers.containsKey(new AnalyzerKey(null, Language.CHINESE_TRADITIONAL, null)))
            addDefaultFor(Language.CHINESE_SIMPLIFIED, new CJKAnalyzer(), analyzers);
        addDefaultFor(Language.CHINESE_TRADITIONAL, new CJKAnalyzer(), analyzers);
        addDefaultFor(Language.JAPANESE, new CJKAnalyzer(), analyzers);
        addDefaultFor(Language.KOREAN, new CJKAnalyzer(), analyzers);
        addDefaultFor(Language.KURDISH, new SoraniAnalyzer(), analyzers);
        addDefaultFor(Language.CZECH, new CzechAnalyzer(), analyzers);
        addDefaultFor(Language.DANISH, new DanishAnalyzer(), analyzers);
        addDefaultFor(Language.GERMAN, new GermanAnalyzer(), analyzers);
        addDefaultFor(Language.GREEK, new GreekAnalyzer(), analyzers);
        addDefaultFor(Language.ENGLISH, new EnglishAnalyzer(), analyzers);
        addDefaultFor(Language.SPANISH, new SpanishAnalyzer(), analyzers);
        addDefaultFor(Language.ESTONIAN, new EstonianAnalyzer(), analyzers);
        addDefaultFor(Language.BASQUE, new BasqueAnalyzer(), analyzers);
        addDefaultFor(Language.PERSIAN, new PersianAnalyzer(), analyzers);
        addDefaultFor(Language.FINNISH, new FinnishAnalyzer(), analyzers);
        addDefaultFor(Language.FRENCH, new FrenchAnalyzer(), analyzers);
        addDefaultFor(Language.IRISH, new IrishAnalyzer(), analyzers);
        addDefaultFor(Language.GALICIAN, new GalicianAnalyzer(), analyzers);
        addDefaultFor(Language.HINDI, new HindiAnalyzer(), analyzers);
        addDefaultFor(Language.HUNGARIAN, new HungarianAnalyzer(), analyzers);
        addDefaultFor(Language.ARMENIAN, new ArmenianAnalyzer(), analyzers);
        addDefaultFor(Language.INDONESIAN, new IndonesianAnalyzer(), analyzers);
        addDefaultFor(Language.ITALIAN, new ItalianAnalyzer(), analyzers);
        addDefaultFor(Language.LITHUANIAN, new LithuanianAnalyzer(), analyzers);
        addDefaultFor(Language.LATVIAN, new LatvianAnalyzer(), analyzers);
        addDefaultFor(Language.NEPALI, new NepaliAnalyzer(), analyzers);
        addDefaultFor(Language.DUTCH, new DutchAnalyzer(), analyzers);
        addDefaultFor(Language.NORWEGIAN_BOKMAL, new NorwegianAnalyzer(), analyzers);
        addDefaultFor(Language.PORTUGUESE, new PortugueseAnalyzer(), analyzers);
        addDefaultFor(Language.ROMANIAN, new RomanianAnalyzer(), analyzers);
        addDefaultFor(Language.RUSSIAN, new RussianAnalyzer(), analyzers);
        addDefaultFor(Language.SERBIAN, new SerbianAnalyzer(), analyzers);
        addDefaultFor(Language.SWEDISH, new SwedishAnalyzer(), analyzers);
        addDefaultFor(Language.TAMIL, new TamilAnalyzer(), analyzers);
        addDefaultFor(Language.TELUGU, new TeluguAnalyzer(), analyzers);
        addDefaultFor(Language.THAI, new ThaiAnalyzer(), analyzers);
        addDefaultFor(Language.TURKISH, new TurkishAnalyzer(), analyzers);
        addDefaultFor(null, new StandardAnalyzer(), analyzers);
    }

    private void addDefaultFor(Language language, Analyzer analyzer, Map<AnalyzerKey, Analyzer> analyzers) {
        analyzers.putIfAbsent(new AnalyzerKey(null, language, null), analyzer);
    }

}
