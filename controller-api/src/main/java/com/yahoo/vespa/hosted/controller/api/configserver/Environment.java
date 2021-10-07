// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.configserver;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Environment representation using the same definition as configserver. And allowing
 * serialization/deserialization to/from JSON.
 *
 * @author Ulf Lilleengen
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Environment {
    private final com.yahoo.config.provision.Environment environment;

    public Environment(com.yahoo.config.provision.Environment environment) {
        this.environment = environment;
    }

    @JsonValue
    public String value() {
        return environment.value();
    }

    @Override
    public String toString() {
        return value();
    }

    public com.yahoo.config.provision.Environment getEnvironment() {
        return environment;
    }

    public Environment(String environment) {
        this.environment = com.yahoo.config.provision.Environment.from(environment);
    }
}
