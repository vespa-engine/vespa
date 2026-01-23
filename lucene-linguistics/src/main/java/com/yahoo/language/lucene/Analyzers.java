// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.language.lucene;

import com.yahoo.component.provider.ComponentRegistry;
import com.yahoo.language.Language;
import com.yahoo.language.process.LinguisticsParameters;
import com.yahoo.language.process.StemMode;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.CharFilterFactory;
import org.apache.lucene.analysis.TokenFilterFactory;
import org.apache.lucene.analysis.TokenizerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Analyzers for various linguistics profiles and/or languages.
 *
 * @author dainiusjocas
 * @author bratseth
 */
class Analyzers {

    private final Map<String, ProfileAnalyzers> profileAnalyzers;

    public Analyzers(LuceneAnalysisConfig config, ComponentRegistry<Analyzer> analyzerComponents) {
        profileAnalyzers = new AnalyzersImporter().importAnalyzers(config, analyzerComponents);
    }

    public Analyzer getAnalyzer(LinguisticsParameters parameters) {
        return lookup(new AnalyzerKey(parameters.profile(), parameters.language(), parameters.stemMode()));
    }

    private Analyzer lookup(AnalyzerKey key) {
        if (key.profile() != null) {
            var analyzersForProfile = profileAnalyzers.get(key.profile());
            if (analyzersForProfile != null) {
                var analyzer = analyzersForProfile.lookup(key);
                if (analyzer != null) return analyzer;
            }
        }

        var profilelessAnalyzers = profileAnalyzers.get(null);
        return profilelessAnalyzers.lookup(key.withProfile(null));
    }

}
