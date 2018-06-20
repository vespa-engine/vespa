// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config;

/**
 * A ConfigKey that also uses the def MD5 sum. Used for caching when def payload is user provided.
 *
 * @author Vegard Havdal
 */
public class ConfigCacheKey {

    private final ConfigKey<?> key;
    private final String defMd5;

    /**
     * Constructs a new server key based on the contents of the given {@link ConfigKey} and the def md5 sum.
     * @param key The key to base on
     * @param defMd5 MD5 checksum of the config definition. Never null.
     */
    public ConfigCacheKey(ConfigKey<?> key, String defMd5) {
        this.key = key;
        this.defMd5 = defMd5 == null ? "" : defMd5;
    }

    /**
     * Constructs new key
     *
     * @param name           config definition name
     * @param configIdString Can be null.
     * @param namespace      namespace for this config definition
     * @param defMd5         MD5 checksum of the config definition. Never null.
     */
    ConfigCacheKey(String name, String configIdString, String namespace, String defMd5) {
        this(new ConfigKey<>(name, configIdString, namespace), defMd5);
    }

    @Override
    public int hashCode() {
        return key.hashCode() + 37 * defMd5.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ConfigCacheKey && key.equals(((ConfigCacheKey) o).getKey())
                && defMd5.equals(((ConfigCacheKey)o).defMd5);
    }

    /**
     * The def md5 sum of this key
     *
     * @return md5 sum
     */
    public String getDefMd5() {
        return defMd5;
    }

    public ConfigKey<?> getKey() {
        return key;
    }

    @Override
    public String toString() {
        return key + "," + defMd5;
    }
}
