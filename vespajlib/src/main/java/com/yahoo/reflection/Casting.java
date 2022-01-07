// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.reflection;

import java.util.Optional;

/**
 * Utility methods for doing casting
 *
 * @author Tony Vaagenes
 */
public class Casting {

    /**
     * Returns the casted instance if it is assignment-compatible with targetClass,
     * or empty otherwise.
     *
     * @see Class#isInstance(Object)
     */
    public static <T> Optional<T> cast(Class<T> targetClass, Object instance) {
        return targetClass.isInstance(instance)?
                Optional.of(targetClass.cast(instance)):
                Optional.empty();
    }

}
