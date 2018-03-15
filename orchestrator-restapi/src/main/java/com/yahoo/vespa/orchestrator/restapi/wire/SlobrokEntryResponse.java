// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator.restapi.wire;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * @author hakonhall
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class SlobrokEntryResponse {
    @JsonProperty("name")
    public final String name;

    @JsonProperty("spec")
    public final String spec;

    public SlobrokEntryResponse(String name, String spec) {
        this.name = name;
        this.spec = spec;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SlobrokEntryResponse that = (SlobrokEntryResponse) o;
        return Objects.equals(name, that.name) &&
                Objects.equals(spec, that.spec);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, spec);
    }

    @Override
    public String toString() {
        return "SlobrokEntryResponse{" +
                "name='" + name + '\'' +
                ", spec='" + spec + '\'' +
                '}';
    }
}
