// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.language.significance;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Path;
import java.util.HashMap;

/**
 *
 * @author MariusArhaug
 */
public class DefaultSignificanceModel implements SignificanceModel {
    private final long corpusSize;
    private final HashMap<String, Long> frequencies;
    private final Path path;

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SignificanceModelFile {
        private final String version;
        private final String id;
        private final String description;
        private final long corpusSize;
        private final String language;
        private final HashMap<String, Long> frequencies;
        private final long tokenCount;

        @JsonCreator
        public SignificanceModelFile(
                @JsonProperty("version") String version,
                @JsonProperty("id") String id,
                @JsonProperty("description") String description,
                @JsonProperty("corpus_size") long corpusSize,
                @JsonProperty("language") String language,
                @JsonProperty("frequencies") HashMap<String, Long> frequencies,
                @JsonProperty("token_count") long tokenCount) {
            this.version = version;
            this.id = id;
            this.description = description;
            this.corpusSize = corpusSize;
            this.language = language;
            this.frequencies = frequencies;
            this.tokenCount = tokenCount;
        }

        @JsonProperty("version")
        public String version() { return version; }

        @JsonProperty("id")
        public String id() { return id; }

        @JsonProperty("description")
        public String description() { return description; }

        @JsonProperty("corpus_size")
        public long corpusSize() { return corpusSize; }

        @JsonProperty("language")
        public String language() { return language; }

        @JsonProperty("frequencies")
        public HashMap<String, Long> frequencies() { return frequencies; }

        @JsonProperty("token_count")
        public long tokenCount() { return tokenCount; }

    }

    public DefaultSignificanceModel(Path path) {
        this.path = path;

        ObjectMapper objectMapper = new ObjectMapper();

        try {
            SignificanceModelFile model = objectMapper.readValue(this.path.toFile(), SignificanceModelFile.class);
            this.corpusSize = model.corpusSize;
            this.frequencies = model.frequencies;
        } catch (Exception e) {
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
}
