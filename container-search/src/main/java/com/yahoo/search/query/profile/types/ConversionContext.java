// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.profile.types;

import com.yahoo.language.Language;
import com.yahoo.language.process.Embedder;
import com.yahoo.search.query.profile.compiled.CompiledQueryProfileRegistry;

import java.util.Map;

/**
 * @author bratseth
 */
public class ConversionContext {

    private final String destination;
    private final CompiledQueryProfileRegistry registry;
    private final Map<String, Embedder> embedders;
    private final Language language;

    public ConversionContext(String destination, CompiledQueryProfileRegistry registry, Embedder embedder,
                             Map<String, String> context) {
        this(destination, registry, Map.of(Embedder.defaultEmbedderId, embedder), context);
    }

    public ConversionContext(String destination, CompiledQueryProfileRegistry registry,
                             Map<String, Embedder> embedders,
                             Map<String, String> context) {
        this.destination = destination;
        this.registry = registry;
        this.embedders = embedders;
        this.language = context.containsKey("language") ? Language.fromLanguageTag(context.get("language"))
                                                        : Language.UNKNOWN;
    }

    /** Returns the local name of the field which will receive the converted value (or null when this is empty) */
    public String destination() { return destination; }

    /** Returns the profile registry, or null if none */
    CompiledQueryProfileRegistry registry() {return registry;}

    /** Returns the configured embedder, never null */
    Map<String, Embedder> embedders() { return embedders; }

    /** Returns the language, which is never null but may be UNKNOWN */
    Language language() { return language; }

    /** Returns an empty context */
    public static ConversionContext empty() {
        return new ConversionContext(null, null, Embedder.throwsOnUse.asMap(), Map.of());
    }

}
