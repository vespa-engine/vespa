// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.language.significance.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.yahoo.component.annotation.Inject;
import com.yahoo.language.Language;
import com.yahoo.language.significance.SignificanceModel;
import com.yahoo.language.significance.SignificanceModelRegistry;
import com.yahoo.search.significance.config.SignificanceConfig;

import java.io.IOException;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

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
        ObjectMapper objectMapper = new ObjectMapper();
        for (var model : cfg.model()) {
            try {
                SignificanceModelFile file = objectMapper.readValue(model.path().toFile(), SignificanceModelFile.class);

                for (var pair : file.languages().entrySet()) {
                    addModel(
                            Language.fromLanguageTag(pair.getKey()), new DefaultSignificanceModel(pair.getValue(), file.id()));
                }
            } catch (IOException e) {
                throw new RuntimeException("Failed to load model from " + model.path(), e);
            }
        }
    }

    public DefaultSignificanceModelRegistry(List<Path> models) {
        this.models = new EnumMap<>(Language.class);

        ObjectMapper objectMapper = new ObjectMapper();
        for (var model : models) {
            try {
                SignificanceModelFile file = objectMapper.readValue(model.toFile(), SignificanceModelFile.class);
                for (var pair : file.languages().entrySet()) {
                    addModel(
                            Language.fromLanguageTag(pair.getKey()), new DefaultSignificanceModel(pair.getValue(), file.id()));
                }
            } catch (IOException e) {
                throw new RuntimeException("Failed to load model from " + model, e);
            }
        }
    }

    public void addModel(Language lang, SignificanceModel model) {
        this.models.put(lang, model);
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
