// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.language.significance.impl;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.HashMap;
import java.util.List;

/**
 *
 * @author MariusArhaug
 */

@JsonIgnoreProperties(ignoreUnknown = true)
public class SignificanceModelFile {
    private final String version;
    private final String id;
    private final String description;

    private final HashMap<String, DocumentFrequencyFile> languages;

    @JsonCreator
    public SignificanceModelFile(
            @JsonProperty("version") String version,
            @JsonProperty("id") String id,
            @JsonProperty("description") String description,
            @JsonProperty("languages") HashMap<String, DocumentFrequencyFile> languages) {
        this.version = version;
        this.id = id;
        this.description = description;
        this.languages = languages;
    }

    @JsonProperty("version")
    public String version() { return version; }

    @JsonProperty("id")
    public String id() { return id; }

    @JsonProperty("description")
    public String description() { return description; }

    @JsonProperty("languages")
    public HashMap<String, DocumentFrequencyFile> languages() { return languages; }

    public void addLanguage(String language, DocumentFrequencyFile documentFrequencyFile) {
        languages.put(language, documentFrequencyFile);
    }
}
