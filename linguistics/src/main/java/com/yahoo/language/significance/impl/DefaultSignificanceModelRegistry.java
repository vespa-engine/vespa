// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.language.significance.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yahoo.component.annotation.Inject;
import com.yahoo.language.Language;
import com.yahoo.language.significance.SignificanceModel;
import com.yahoo.language.significance.SignificanceModelRegistry;
import com.yahoo.search.significance.config.SignificanceConfig;
import io.airlift.compress.zstd.ZstdInputStream;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ArrayList;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Default implementation of {@link SignificanceModelRegistry}.
 * This implementation loads models lazily and caches them.
 *
 * @author MariusArhaug
 */
public class DefaultSignificanceModelRegistry implements SignificanceModelRegistry {

    private static final Logger log = Logger.getLogger(DefaultSignificanceModelRegistry.class.getName());

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
        log.fine(() -> "Loading model from " + path);
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            InputStream in = path.toString().endsWith(".zst") ?
                    new ZstdInputStream(new FileInputStream(path.toFile())) :
                    new FileInputStream(path.toFile());

            SignificanceModelFile file = objectMapper.readValue(in, SignificanceModelFile.class);
            for (var pair : file.languages().entrySet()) {

                var languagesStr = pair.getKey();
                log.fine(() -> "Found model for languages '%s'".formatted(languagesStr));
                String[] languageTags = languagesStr.split(",");

                for (var languageTag : languageTags) {
                    var language = Language.fromLanguageTag(languageTag);
                    log.fine(() -> "Adding model for language %s with id %s".formatted(language, file.id()));
                    this.models.put(language, new DefaultSignificanceModel(pair.getValue(), file.id()));
                }
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
