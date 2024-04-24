// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.language.significance.impl;

import com.yahoo.component.annotation.Inject;
import com.yahoo.language.Language;
import com.yahoo.language.significance.SignificanceModel;
import com.yahoo.language.significance.SignificanceModelRegistry;
import com.yahoo.search.significance.config.SignificanceConfig;

import java.nio.file.Path;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import static com.yahoo.yolean.Exceptions.uncheck;
/**
 * Default implementation of {@link SignificanceModelRegistry}.
 * This implementation loads models lazily and caches them.
 *
 * @author MariusArhaug
 */
public class DefaultSignificanceModelRegistry implements SignificanceModelRegistry {

    private final Map<Language, SignificanceModel> models;
    @Inject
    public DefaultSignificanceModelRegistry(SignificanceConfig cfg) { this(new Builder(cfg)); }
    private DefaultSignificanceModelRegistry(Builder b) {
        this.models = new EnumMap<>(Language.class);
        b.models.forEach((language, path) -> {
            models.put(language,
                    uncheck(() -> new DefaultSignificanceModel(path)));
        });
    }

    public DefaultSignificanceModelRegistry(HashMap<Language, Path> map) {
        this.models = new EnumMap<>(Language.class);
        map.forEach((language, path) -> {
            models.put(language,
                    uncheck(() -> new DefaultSignificanceModel(path)));
        });
    }


    @Override
    public Optional<SignificanceModel> getModel(Language language) {
        if (!models.containsKey(language))
        {
            return Optional.empty();
        }
        return Optional.of(models.get(language));
    }


    public static final class Builder {
        private final Map<Language, Path> models = new EnumMap<>(Language.class);

        public Builder() {}
        public Builder(SignificanceConfig cfg) {
            for (var model : cfg.model()) {
                addModel(Language.fromLanguageTag(model.language()), model.path());
            }
        }

        public Builder addModel(Language lang, Path path) { models.put(lang, path); return this; }
        public DefaultSignificanceModelRegistry build() { return new DefaultSignificanceModelRegistry(this); }
    }

}
