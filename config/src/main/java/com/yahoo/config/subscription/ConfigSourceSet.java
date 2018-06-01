// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.subscription;

import com.yahoo.log.LogLevel;

import java.util.*;
import java.util.logging.Logger;


/**
 * An immutable set of connection endpoints, where each endpoint points to either a
 * remote config server or a config proxy.
 *
 * Two sets are said to be equal if they contain the same sources, independent of order,
 * upper/lower-casing and whitespaces.
 *
 * @author gjoranv
 */
public class ConfigSourceSet implements ConfigSource {

    private static final Logger log = Logger.getLogger(ConfigSourceSet.class.getName());
    private final Set<String> sources = new LinkedHashSet<>();

    /**
     * Creates an empty ConfigSourceSet, mostly used for unit testing.
     */
    public ConfigSourceSet() {
    }

    /**
     * Creates a ConfigSourceSet containing all the unique given input addresses.
     * Each address is trimmed and lower-cased before adding.
     *
     * @param addresses  Connection endpoints on the format "tcp/host:port".
     */
    public ConfigSourceSet(List<String> addresses) {
        for (String a : addresses) {
           sources.add(a.trim().toLowerCase());
        }
    }

    /**
     * Creates a ConfigSourceSet containing all the unique given input addresses.
     * Each address is trimmed and lower-cased before adding.
     *
     * @param addresses  Connection endpoints on the format "tcp/host:port".
     */
    public ConfigSourceSet(String[] addresses) {
        this(Arrays.asList(addresses));
    }

    /**
     * Convenience constructor to create a ConfigSourceSet with only one input address.
     *
     * @param address  Connection endpoint on the format "tcp/host:port".
     */
    public ConfigSourceSet(String address) {
        this(new String[] {address});
    }

    /**
     * Returns an unmodifiable set containing all sources in this ConfigSourceSet. Iteration order is
     * guaranteed to be the same as that of the list or array that was given when this set was created.
     *
     * @return All sources in this ConfigSourceSet.
     */
    public Set<String> getSources() {
        return Collections.unmodifiableSet(sources);
    }

    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (! (o instanceof ConfigSourceSet)) {
            return false;
        }
        ConfigSourceSet css = (ConfigSourceSet)o;
        return sources.equals(css.sources);
    }

    public int hashCode() {
        return sources.hashCode();
    }

    public String toString() {
        return sources.toString();
    }

    /**
     * Create a new source set using the environment variables or system properties
     * @return a new source set if available, null if not.
     */
    public static ConfigSourceSet createDefault() {
        String configSources = System.getenv("VESPA_CONFIG_SOURCES");
        if (configSources != null) {
            log.log(LogLevel.INFO, "Using config sources from VESPA_CONFIG_SOURCES: " + configSources);
            return new ConfigSourceSet(checkSourcesSyntax(configSources));
        } else {
            String[] def = {"tcp/localhost:" + System.getProperty("vespa.config.port", "19090")};
            String[] sourceSet = checkSourcesSyntax(System.getProperty("configsources"));
            return new ConfigSourceSet(sourceSet == null ? def : sourceSet);
        }
    }

    /**
     * Check sources syntax and convert it to a proper source set by checking if
     * sources start with the required "tcp/" prefix and add that prefix if not.
     *
     * @param sources a source set as a comma-separated string
     * @return a String array with sources, or null if the input source set was null
     */
    private static String[] checkSourcesSyntax(String sources) {
        String[] sourceSet = null;
        if (sources != null) {
            sourceSet = sources.split(",");
            int i = 0;
            for (String s : sourceSet) {
                if (!s.startsWith("tcp/")) {
                    sourceSet[i] = "tcp/" + sourceSet[i];
                }
                i++;
            }
        }
        return sourceSet;
    }

}
