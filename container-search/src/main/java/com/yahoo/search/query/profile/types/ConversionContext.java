// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.profile.types;

import com.yahoo.language.Language;
import com.yahoo.language.process.Embedder;
import com.yahoo.search.query.Properties;
import com.yahoo.search.query.profile.compiled.CompiledQueryProfileRegistry;
import com.yahoo.search.query.properties.PropertyMap;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * @author bratseth
 */
public class ConversionContext {

    private final String destination;
    private final CompiledQueryProfileRegistry registry;
    private final Map<String, Embedder> embedders;
    private final Map<String, String> contextValues;
    private final Properties properties;
    private final Language language;
    private final Optional<Instant> deadline;

    public ConversionContext(String destination, CompiledQueryProfileRegistry registry, Embedder embedder,
                             Map<String, String> context, Properties properties) {
        this(destination, registry, Map.of(Embedder.defaultEmbedderId, embedder), context, properties);
    }

    public ConversionContext(String destination, CompiledQueryProfileRegistry registry,
                             Map<String, Embedder> embedders,
                             Map<String, String> context,
                             Properties properties) {
        this(destination, registry, embedders, context, properties, Optional.empty());
    }

    /**
     * Creates a context for conversions which must complete by the given deadline.
     *
     * @param deadline the time by which a conversion must complete, or empty for no deadline
     */
    public ConversionContext(String destination, CompiledQueryProfileRegistry registry,
                             Map<String, Embedder> embedders,
                             Map<String, String> context,
                             Properties properties,
                             Optional<Instant> deadline) {
        this.destination = destination;
        this.registry = registry;
        this.embedders = embedders;

        // If this was set in the request it may not be in this properties instance, which may be just the query profile
        // properties, which are below the queryProperties in the chain ...
        Object language = context.get("language"); // set in the request
        if (language == null)
            language = properties.get("language", context);
        this.language = language != null ? Language.fromLanguageTag(language.toString()) : Language.UNKNOWN;

        this.contextValues = context;
        this.properties = properties;
        this.deadline = Objects.requireNonNull(deadline, "deadline");
    }

    /** Returns the local name of the field which will receive the converted value (or null when this is empty) */
    public String destination() { return destination; }

    /** Returns the profile registry, or null if none */
    CompiledQueryProfileRegistry registry() {return registry;}

    /** Returns the configured embedder, never null */
    Map<String, Embedder> embedders() { return embedders; }

    /** Returns the language, which is never null but may be UNKNOWN */
    Language language() { return language; }

    /** Returns a read-only map of context key-values which can be looked up during conversion. */
    Map<String, String> contextValues() { return contextValues; }

    /**
     * Returns properties that can supply values referenced during conversion.
     * This contains the context values as well, but may also contain additional values, e.g. from query profiles.
     */
    Properties properties() { return properties; }

    /** Returns the time by which a conversion must complete, or empty if it has no deadline */
    Optional<Instant> deadline() { return deadline; }

    /** Returns an empty context */
    public static ConversionContext empty() {
        return new ConversionContext(null, null, Embedder.throwsOnUse.asMap(), Map.of(), new PropertyMap());
    }

}
