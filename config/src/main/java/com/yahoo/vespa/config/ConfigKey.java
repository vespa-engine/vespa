// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config;

import com.yahoo.config.ConfigInstance;
import com.yahoo.config.ConfigurationRuntimeException;

/**
 * Class for holding the key when doing cache look-ups and other management of config instances.
 *
 * @author hmusum
 */
public class ConfigKey<CONFIGCLASS extends ConfigInstance> implements Comparable<ConfigKey<?>> {

    private final String name;
    private final String configId;
    private final String namespace;

    // The two fields below are only set when ConfigKey is constructed from a config class. Can be null
    private final Class<CONFIGCLASS> configClass;

    /**
     * Constructs new key
     *
     * @param name           config definition name
     * @param configIdString Can be null.
     * @param namespace      namespace for this config definition
     */
    public ConfigKey(String name, String configIdString, String namespace) {
        this(name, configIdString, namespace, null);
    }

    /**
     * Creates a new instance from the given class and configId
     *
     * @param clazz          Config class
     * @param configIdString config id, can be null.
     */
    public ConfigKey(Class<CONFIGCLASS> clazz, String configIdString) {
        this(getFieldFromClass(clazz, "CONFIG_DEF_NAME"),
             configIdString,
             getFieldFromClass(clazz, "CONFIG_DEF_NAMESPACE"),
             clazz);
    }

    public ConfigKey(String name, String configIdString, String namespace, Class<CONFIGCLASS> clazz) {
        if (name == null || name.isEmpty())
            throw new ConfigurationRuntimeException("Config name cannot be null or empty!");
        if (namespace == null || namespace.isEmpty())
            throw new ConfigurationRuntimeException("Config namespace cannot be null or empty!");
        this.name = name;
        this.configId = (configIdString == null) ? "" : configIdString;
        this.namespace = namespace;
        this.configClass = clazz;
    }

    /**
     * Comparison sort order: namespace, name, configId.
     */
    @Override
    public int compareTo(ConfigKey<?> o) {
        if (!o.getNamespace().equals(getNamespace())) return getNamespace().compareTo(o.getNamespace());
        if (!o.getName().equals(getName())) return getName().compareTo(o.getName());
        return getConfigId().compareTo(o.getConfigId());
    }

    private static String getFieldFromClass(Class<?> clazz, String fieldName) {
        try {
            return (String) clazz.getField(fieldName).get(null);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new ConfigurationRuntimeException("No such field '" + fieldName + "' in class " + clazz + ", or could not access field.", e);
        }
    }

    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof ConfigKey)) {
            return false;
        }
        ConfigKey<?> key = (ConfigKey<?>) o;
        return (name.equals(key.name) &&
                configId.equals(key.configId) &&
                namespace.equals(key.namespace));
    }

    public int hashCode() {
        int hash = 17;
        hash = 37 * hash + name.hashCode();
        hash = 37 * hash + configId.hashCode();
        hash = 37 * hash + namespace.hashCode();
        return hash;
    }

    public String getName() {
        return name;
    }

    public String getConfigId() {
        return configId;
    }

    public String getNamespace() {
        return namespace;
    }

    public Class<CONFIGCLASS> getConfigClass() {
        return configClass;
    }

    public String toString() { return "name=" + namespace  + "." + name  + ",configId=" + configId; }

    public static ConfigKey<?> createFull(String name, String configId, String namespace) {
        return new ConfigKey<>(name, configId, namespace, null);
    }

}
