// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.chef.rest;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * @author mortent
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class CookBook {
    public final String name;
    public final List<Attributes> attributes;

    public CookBook(@JsonProperty("name") String name, @JsonProperty("attributes") List<Attributes> attributes) {
        this.name = name;
        this.attributes = attributes;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Attributes {
        public final String name;
        public final String url;

        public Attributes(@JsonProperty("name") String name, @JsonProperty("url") String url) {
            this.name = name;
            this.url = url;
        }
    }
}
