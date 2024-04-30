// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.language.significance.impl;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.HashMap;

/**
 *
 * @author MariusArhaug
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DocumentFrequencyFile {
    private final String description;

    private final int documentCount;


    private final HashMap<String, Long> frequencies;

    @JsonCreator
    public DocumentFrequencyFile(
            @JsonProperty("description") String description,
            @JsonProperty("document-count") int documentCount,
            @JsonProperty("document-frequencies") HashMap<String, Long> frequencies) {
        this.description = description;
        this.documentCount = documentCount;
        this.frequencies = frequencies;
    }

    @JsonProperty("description")
    public String description() { return description; }

    @JsonProperty("document-count")
    public int documentCount() { return documentCount; }

    @JsonProperty("document-frequencies")
    public HashMap<String, Long> frequencies() { return frequencies; }
}
