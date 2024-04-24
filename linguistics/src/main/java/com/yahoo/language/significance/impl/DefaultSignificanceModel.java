// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.language.significance.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yahoo.language.significance.DocumentFrequency;
import com.yahoo.language.significance.SignificanceModel;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;

/**
 *
 * @author MariusArhaug
 */
public class DefaultSignificanceModel implements SignificanceModel {
    private final long corpusSize;
    private final HashMap<String, Long> frequencies;

    private String id;

    public DefaultSignificanceModel(DocumentFrequencyFile file, String id) {
        this.frequencies = file.frequencies();
        this.corpusSize = file.documentCount();
        this.id = id;
    }

    public DefaultSignificanceModel(Path path) {
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            var file         = objectMapper.readValue(path.toFile(), DocumentFrequencyFile.class);
            this.frequencies = file.frequencies();
            this.corpusSize  = file.documentCount();
        } catch (IOException e) {
            throw new RuntimeException("Failed to load model from " + path, e);
        }
    }

    @Override
    public DocumentFrequency documentFrequency(String word) {
        if (frequencies.containsKey(word)) {
            return new DocumentFrequency(frequencies.get(word), corpusSize);
        }
        return new DocumentFrequency(1, corpusSize);
    }

    @Override
    public String getId() {
        return this.id;
    }

}
