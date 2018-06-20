// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config;

import com.yahoo.config.ConfigInstance;
import com.yahoo.config.ConfigurationRuntimeException;
import com.yahoo.config.codegen.CNode;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * Class for holding the key when doing cache look-ups and other management of config instances.
 *
 * @author hmusum
 */
public class ConfigKey<CONFIGCLASS extends ConfigInstance> implements Comparable<ConfigKey<?>> {

    @NonNull
    private final String name;
    @NonNull
    private final String configId;
    @NonNull
    private final String namespace;

    // The two fields below are only set when ConfigKey is constructed from a config class. Can be null
    private final Class<CONFIGCLASS> configClass;
    private final String md5; // config definition md5

    /**
     * Constructs new key
     *
     * @param name           config definition name
     * @param configIdString Can be null.
     * @param namespace      namespace for this config definition
     */
    public ConfigKey(String name, String configIdString, String namespace) {
        this(name, configIdString, namespace, null, null);
    }

    /**
     * Creates a new instance from the given class and configId
     *
     * @param clazz          Config class
     * @param configIdString config id, can be null.
     */
    public ConfigKey(Class<CONFIGCLASS> clazz, String configIdString) {
        this(getFieldFromClass(clazz, "CONFIG_DEF_NAME"),
                configIdString, getFieldFromClass(clazz, "CONFIG_DEF_NAMESPACE"), getFieldFromClass(clazz, "CONFIG_DEF_MD5"), clazz);
    }

    public ConfigKey(String name, String configIdString, String namespace, String defMd5, Class<CONFIGCLASS> clazz) {
        if (name == null)
            throw new ConfigurationRuntimeException("Config name must be non-null!");
        this.name = name;
        this.configId = (configIdString == null) ? "" : configIdString;
        this.namespace = (namespace == null) ? CNode.DEFAULT_NAMESPACE : namespace;
        this.md5 = (defMd5 == null) ? "" : defMd5;
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
        ConfigKey<?> key = (ConfigKey) o;
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

    @NonNull
    public String getName() {
        return name;
    }

    @NonNull
    public String getConfigId() {
        return configId;
    }

    @NonNull
    public String getNamespace() {
        return namespace;
    }

    @Nullable
    public Class<CONFIGCLASS> getConfigClass() {
        return configClass;
    }

    @Nullable
    public String getMd5() {
        return md5;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("name=");
        sb.append(name);
        sb.append(",namespace=");
        sb.append(namespace);
        sb.append(",configId=");
        sb.append(configId);
        return sb.toString();
    }

    public static ConfigKey<?> createFull(String name, String configId, String namespace, String md5) {
        return new ConfigKey<>(name, configId, namespace, md5, null);
    }

}
