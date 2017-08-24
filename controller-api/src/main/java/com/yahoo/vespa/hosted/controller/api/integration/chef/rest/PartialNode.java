// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.chef.rest;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;
import java.util.Optional;

/**
 * @author mortent
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class PartialNode {

    @JsonProperty("data")
    private final Map<String, String> data;

    @JsonCreator
    public PartialNode(@JsonProperty("data") Map<String, String> data) {
        this.data = data;
    }

    public Optional<String> getValue(String key) {
        return Optional.ofNullable(data.get(key));
    }

    public String getFqdn() {
        return getValue("fqdn").orElse("");
    }

    public String getName() {
        return getValue("name").orElse("");
    }

    public Double getOhaiTime() {
        return Double.parseDouble(getValue("ohai_time").orElse("0.0"));
    }
}
