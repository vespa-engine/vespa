// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags.custom;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

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
        int id,
        String name,
        String image,
        SidecarResources resources,
        List<String> volumeMounts,
        Map<String, String> envs,
        List<String> command) {
    public Sidecar {
        if (id < 0 || id > 99) {
            throw new IllegalArgumentException("Sidecar id must be between 0 and 99");
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
