// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator.resources;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.yahoo.jrt.slobrok.api.Mirror;

public class SlobrokEntryResponse {
    @JsonProperty("name")
    public final String name;

    @JsonProperty("spec")
    public final String spec;

    static SlobrokEntryResponse fromMirrorEntry(Mirror.Entry entry) {
        return new SlobrokEntryResponse(entry.getName(), entry.getSpec());
    }

    private SlobrokEntryResponse(String name, String spec) {
        this.name = name;
        this.spec = spec;
    }
}
