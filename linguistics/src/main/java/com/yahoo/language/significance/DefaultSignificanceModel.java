package com.yahoo.language.significance;

import wiremock.com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Path;
import java.util.HashMap;

public class DefaultSignificanceModel implements SignificanceModel {
    private final long corpus_size;
    private final HashMap<String, Long> frequencies;
    private final Path path;

    private static class SignificanceModelFile {
        String version;
        String id;
        String description;
        long corpus_size;
        String language;
        HashMap<String, Long> frequencies;
    }

    public DefaultSignificanceModel(Path path) {
        this.path = path;

        ObjectMapper objectMapper = new ObjectMapper();

        try {
            SignificanceModelFile model = objectMapper.readValue(this.path.toFile(), SignificanceModelFile.class);
            this.corpus_size = model.corpus_size;
            this.frequencies = model.frequencies;
        } catch (Exception e) {
            throw new RuntimeException("Failed to load model from " + path, e);
        }
    }

    @Override
    public DocumentFrequency documentFrequency(String word) {
        if (frequencies.containsKey(word)) {
            return new DocumentFrequency(frequencies.get(word), corpus_size);
        }
        return new DocumentFrequency(1, corpus_size);
    }
}
