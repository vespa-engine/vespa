// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.chef.rest;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * @author mpolden
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Client {

    @JsonProperty("name")
    public String name;
    @JsonProperty("validator")
    public boolean validator;

    @Override
    public String toString() {
        return "Client{" +
                "name='" + name + '\'' +
                ", validator=" + validator +
                '}';
    }
}
