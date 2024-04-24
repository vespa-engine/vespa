// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.language.significance.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yahoo.component.annotation.Inject;
import com.yahoo.language.Language;
import com.yahoo.language.significance.SignificanceModel;
import com.yahoo.language.significance.SignificanceModelRegistry;
import com.yahoo.search.significance.config.SignificanceConfig;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Default implementation of {@link SignificanceModelRegistry}.
 * This implementation loads models lazily and caches them.
 *
 * @author MariusArhaug
 */
public class DefaultSignificanceModelRegistry implements SignificanceModelRegistry {

    private final Map<Language, SignificanceModel> models;

    @Inject
    public DefaultSignificanceModelRegistry(SignificanceConfig cfg) {
        this.models = new EnumMap<>(Language.class);
        for (var model : cfg.model()) {
           addModel(model.path());
        }
    }

    public DefaultSignificanceModelRegistry(List<Path> models) {
        this.models = new EnumMap<>(Language.class);
        for (var path : models) {
            addModel(path);
        }
    }

    public void addModel(Path path) {
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            SignificanceModelFile file = objectMapper.readValue(path.toFile(), SignificanceModelFile.class);
            for (var pair : file.languages().entrySet()) {
                this.models.put(
                        Language.fromLanguageTag(pair.getKey()),
                        new DefaultSignificanceModel(pair.getValue(), file.id()));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load model from " + path, e);
        }
    }

    @Override
    public Optional<SignificanceModel> getModel(Language language) {
        if (!models.containsKey(language))
        {
            return Optional.empty();
        }
        return Optional.of(models.get(language));
    }
}
