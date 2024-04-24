package com.yahoo.language.significance.impl;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.HashMap;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DocumentFrequencyFile {
    private final String description;

    private final String language;

    private final int documentCount;

    private final int wordCount;

    private final HashMap<String, Long> frequencies;

    @JsonCreator
    public DocumentFrequencyFile(
            @JsonProperty("description") String description,
            @JsonProperty("document-count") int documentCount,
            @JsonProperty("word-count") int wordCount,
            @JsonProperty("language") String language,
            @JsonProperty("document-frequencies") HashMap<String, Long> frequencies) {
        this.description = description;
        this.documentCount = documentCount;
        this.wordCount = wordCount;
        this.language = language;
        this.frequencies = frequencies;
    }

    @JsonProperty("description")
    public String description() { return description; }

    @JsonProperty("document-count")
    public int documentCount() { return documentCount; }

    @JsonProperty("language")
    public String language() { return language; }

    @JsonProperty("word-count")
    public int wordCount() { return wordCount; }

    @JsonProperty("document-frequencies")
    public HashMap<String, Long> frequencies() { return frequencies; }
}
