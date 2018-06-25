// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.di.componentgraph.core;

import com.google.inject.Key;
import com.yahoo.config.ConfigInstance;
import com.yahoo.vespa.config.ConfigKey;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

/**
 * @author ollivir
 */
public class Keys {
    static Key<?> createKey(Type instanceType, Annotation annotation) {
        if (annotation == null) {
            return Key.get(instanceType);
        } else {
            return Key.get(instanceType, annotation);
        }
    }

    @SuppressWarnings("unchecked")
    public static Map<ConfigKey<ConfigInstance>, ConfigInstance> invariantCopy(Map<ConfigKey<? extends ConfigInstance>, ConfigInstance> configs) {
        Map<ConfigKey<ConfigInstance>, ConfigInstance> ret = new HashMap<>();
        configs.forEach((k, v) -> ret.put((ConfigKey<ConfigInstance>) k, v));
        return ret;
    }

    public static Map<ConfigKey<? extends ConfigInstance>, ConfigInstance> covariantCopy(Map<ConfigKey<ConfigInstance>, ConfigInstance> configs) {
        Map<ConfigKey<? extends ConfigInstance>, ConfigInstance> ret = new HashMap<>();
        configs.forEach((k, v) -> ret.put(k, v));
        return ret;
    }
}
