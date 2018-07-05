// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.core;

import com.google.common.collect.ImmutableMap;
import com.google.inject.AbstractModule;
import com.google.inject.name.Names;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * @author Simon Thoresen Hult
 */
class ApplicationConfigModule extends AbstractModule {

    private final Map<String, String> config;

    ApplicationConfigModule(Map<String, String> config) {
        this.config = normalizeConfig(config);
    }

    @Override
    protected void configure() {
        for (Map.Entry<String, String> entry : config.entrySet()) {
            bind(String.class).annotatedWith(Names.named(entry.getKey())).toInstance(entry.getValue());
        }
    }

    public static ApplicationConfigModule newInstanceFromFile(String fileName) throws IOException {
        Properties props = new Properties();
        InputStream in = null;
        try {
            in = new FileInputStream(fileName);
            props.load(in);
        } finally {
            if (in != null) {
                in.close();
            }
        }
        Map<String, String> ret = new HashMap<>();
        for (String name : props.stringPropertyNames()) {
            ret.put(name, props.getProperty(name));
        }
        return new ApplicationConfigModule(ret);
    }

    private static Map<String, String> normalizeConfig(Map<String, String> raw) {
        List<String> names = new ArrayList<>(raw.keySet());
        Collections.sort(names, new Comparator<String>() {

            @Override
            public int compare(String lhs, String rhs) {
                return -lhs.compareTo(rhs); // reverse alphabetical order, i.e. lower-case before upper-case
            }
        });
        Map<String, String> ret = new HashMap<>();
        for (String name : names) {
            ret.put(name.toLowerCase(Locale.US), raw.get(name));
        }
        return ImmutableMap.copyOf(ret);
    }
}
