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

    private final CompiledQueryProfileRegistry registry;
    private final Embedder embedder;
    private final Language language;

    public ConversionContext(CompiledQueryProfileRegistry registry, Embedder embedder, Map<String, String> context) {
        this.registry = registry;
        this.embedder = embedder;
        this.language = context.containsKey("language") ? Language.fromLanguageTag(context.get("language"))
                                                        : Language.UNKNOWN;
    }

    /** Returns the profile registry, or null if none */
    CompiledQueryProfileRegistry getRegistry() {return registry;}

    /** Returns the configured encoder, never null */
    Embedder getEncoder() { return embedder; }

    /** Returns the language, which is never null but may be UNKNOWN */
    Language getLanguage() { return language; }

    /** Returns an empty context */
    public static ConversionContext empty() {
        return new ConversionContext(null, Embedder.throwsOnUse, Map.of());
    }

}
