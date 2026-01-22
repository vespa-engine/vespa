// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.language.lucene;

import com.yahoo.language.Language;
import org.apache.lucene.analysis.Analyzer;

import java.util.Map;

/**
 * Analyzers for a single analyzer profile:
 *
 * @author bratseth
 */
public class ProfileAnalyzers {

    private final Map<AnalyzerKey, Analyzer> analyzers;

    public ProfileAnalyzers(Map<AnalyzerKey, Analyzer> analyzers) {
        this.analyzers = analyzers;
    }

    public Analyzer lookup(AnalyzerKey key) {
        var analyzer = lookupWithChineseFallback(key);
        if (analyzer != null) return analyzer;

        if (key.stemMode() != null) {
            analyzer = lookupWithChineseFallback(key.withStemMode(null));
            if (analyzer != null) return analyzer;
        }

        return analyzers.get(key.withStemMode(null).withLanguage(null));
    }

    private Analyzer lookupWithChineseFallback(AnalyzerKey key) {
        var analyzer = analyzers.get(key);
        if (analyzer != null) return analyzer;

        if (key.language() == Language.CHINESE_SIMPLIFIED) {
            analyzer = analyzers.get(key.withLanguage(Language.CHINESE_TRADITIONAL));
            if (analyzer != null) return analyzer;
        }

        return null;
    }

}
