// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config;

import java.util.Objects;

/**
 * A config definition key: name, namespace)
 *
 * @author bratseth
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
        require(namespace, "A config namespace cannot be null or empty");
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

    public String getName() { return name; }

    public String getNamespace() { return namespace; }

    public String asFileName() {
        return namespace + "." + name + ".def";
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if ( ! (o instanceof ConfigDefinitionKey)) return false;

        ConfigDefinitionKey other = (ConfigDefinitionKey)o;
        return name.equals(other.getName()) && namespace.equals(other.getNamespace());
    }

    @Override
    public int hashCode() {
        return namespace.hashCode() + name.hashCode();
    }

    @Override
    public String toString() {
        return namespace + "." + name;
    }

}
