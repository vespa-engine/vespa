// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.builder.xml.dom.chains;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

/**
 * Utility class for instantiating a builder using reflection.
 *
 * @author Tony Vaagenes
 */
public class DomBuilderCreator {

    public static <T> T create(Class<T> builderClass, Object... parameters) {
        try {
            return getConstructor(builderClass).newInstance(parameters);
        } catch (InstantiationException | InvocationTargetException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> Constructor<T> getConstructor(Class<T> builderClass) {
        Constructor<?>[] constructors = builderClass.getConstructors();
        assert(constructors.length == 1);
        return (Constructor<T>) constructors[0];
    }

}
