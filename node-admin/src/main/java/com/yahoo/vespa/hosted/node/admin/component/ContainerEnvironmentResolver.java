// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.component;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yahoo.vespa.hosted.node.admin.ContainerNodeSpec;

import java.util.HashMap;
import java.util.Map;

/**
 * For creating environment variables that should be available inside a Docker container.
 * An environment variable CONTAINER_ENVIRONMENT_SETTINGS with the settings here will be
 * available inside the container. Serializing happens in ContainerEnvironmentSetting in this interface.
 *
 * @author hmusum
 */
public interface ContainerEnvironmentResolver {

    String createSettings(Environment environment, ContainerNodeSpec nodeSpec);

    class ContainerEnvironmentSettings {

        @JsonProperty(value = "environmentVariables")
        private final Map<String, Object> variables = new HashMap<>();

        public ContainerEnvironmentSettings set(String argument, Object value) {
            variables.put(argument, value);
            return this;
        }

        public String build() {
            ObjectMapper objectMapper = new ObjectMapper();
            try {
                return objectMapper.writeValueAsString(this);
            } catch (JsonProcessingException e) {
                throw new IllegalArgumentException("Could not write " + this, e);
            }
        }
    }

}
