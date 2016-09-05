// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config;

import java.util.Objects;

/**
 * Represents one config definition key (name, namespace)
 *
 * @author vegardh
 */
public class ConfigDefinitionKey {

    private final String name;
    private final String namespace;

    /**
     * Creates a config definition key.
     * 
     * @param name      config definition name
     * @param namespace config definition namespace
     */
    public ConfigDefinitionKey(String name, String namespace) {
        require(name, "A config name cannot be null or empty");
        require(namespace, "A config name cannot be null or empty");
        this.name = name;
        this.namespace = namespace;
    }
    
    private static void require(String object, String message) {
        Objects.requireNonNull(object, message);
        if (object.isEmpty())
            throw new IllegalArgumentException(message);
    }

    public ConfigDefinitionKey(ConfigKey<?> key) {
        this(key.getName(), key.getNamespace());
    }

    public String getName() {
        return name;
    }

    public String getNamespace() {
        return namespace;
    }

    @Override
    public boolean equals(Object oth) {
        if (!(oth instanceof ConfigDefinitionKey)) {
            return false;
        }
        ConfigDefinitionKey other = (ConfigDefinitionKey) oth;
        return name.equals(other.getName()) &&
                namespace.equals(other.getNamespace());
    }

    @Override
    public int hashCode() {
        return namespace.hashCode() + name.hashCode();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(namespace).append(".").append(name);
        return sb.toString();
    }

}
