// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags.custom;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * Defines properties for sidecar flag.
 *
 * @author glebashnik
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(value = JsonInclude.Include.NON_NULL)
public record Sidecar(
        @JsonProperty("id") int id,
        @JsonProperty("name") String name,
        @JsonProperty("image") String image,
        @JsonProperty("resources") SidecarResources resources,
        @JsonProperty("volumeMounts") List<String> volumeMounts,
        @JsonProperty("envs") Map<String, String> envs,
        @JsonProperty("command") List<String> command) {
    private static final int MIN_ID = 0;
    private static final int MAX_ID = 99;
    
    @JsonCreator
    public Sidecar {
        if (id < MIN_ID || id > MAX_ID) {
            throw new IllegalArgumentException("Sidecar id must be between 0 and 99, actual: %s".formatted(id));
        }
        
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Sidecar name must be specified");
        }
        
        if (image == null || image.isBlank()) {
            throw new IllegalArgumentException("Sidecar image must be specified");
        }
        
        resources = resources == null ? SidecarResources.DEFAULT : resources;
        volumeMounts = volumeMounts == null ? List.of() : volumeMounts;
        envs = envs == null ? Map.of() : envs;
        command = command == null ? List.of() : command;
    }
}
