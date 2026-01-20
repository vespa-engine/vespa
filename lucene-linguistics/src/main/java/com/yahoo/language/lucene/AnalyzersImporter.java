// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.language.lucene;

import com.yahoo.collections.ListMap;
import com.yahoo.component.provider.ComponentRegistry;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.CharFilterFactory;
import org.apache.lucene.analysis.TokenFilterFactory;
import org.apache.lucene.analysis.TokenizerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Creates all available analyzers and places them in a lookup-friendly structure.
 *
 * @author dainiusjocas
 * @author bratseth
 */
public class AnalyzersImporter {

    private static final Logger log = Logger.getLogger(AnalyzersImporter.class.getName());

    Map<String, ProfileAnalyzers> importAnalyzers(LuceneAnalysisConfig config,
                                                  ComponentRegistry<Analyzer> components) {
        logConfig(components);
        var profileAnalyzers = new HashMap<String, ProfileAnalyzers>();
        // Keys for each found profile (including the default null profile if analyzers with no profile are explicitly configured)
        ListMap<String, AnalyzerKey> keys = new ListMap<>();
        addKeysFrom(config, keys);
        addKeysFrom(components, keys);

        var profileImporter = new ProfileAnalyzersImporter();
        for (var profileKeys : keys.entrySet()) {
            profileAnalyzers.put(profileKeys.getKey(), profileImporter.importAnalyzers(profileKeys.getKey(),
                                                                                       Set.copyOf(profileKeys.getValue()),
                                                                                       config,
                                                                                       components));
        }
        if ( ! profileAnalyzers.containsKey(null)) // nothing explicitly configured with no profile: Add defaults
            profileAnalyzers.put(null, profileImporter.importAnalyzers(null, Set.of(), config, components));
        return profileAnalyzers;
    }

    private void addKeysFrom(LuceneAnalysisConfig config, ListMap<String, AnalyzerKey> keys) {
        config.analysis().keySet().stream()
              .map(AnalyzerKey::fromString)
              .forEach(key -> keys.put(key.profile(), key));
    }

    private void addKeysFrom(ComponentRegistry<Analyzer> components, ListMap<String, AnalyzerKey> keys) {
        components.allComponentsById().keySet().stream()
                  .map(id -> AnalyzerKey.fromString(id.toString()))
                  .forEach(key -> keys.put(key.profile(), key));
    }

    private static void logConfig(ComponentRegistry<Analyzer> components) {
        log.config("Available in classpath char filters: " + CharFilterFactory.availableCharFilters());
        log.config("Available in classpath tokenizers: " + TokenizerFactory.availableTokenizers());
        log.config("Available in classpath token filters: " + TokenFilterFactory.availableTokenFilters());
        log.config("Available in component registry: " + dump(components));
    }

    private static String dump(ComponentRegistry<Analyzer> components) {
        StringBuilder buf = new StringBuilder();
        buf.append("[");
        var map = components.allComponentsById();
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

}
