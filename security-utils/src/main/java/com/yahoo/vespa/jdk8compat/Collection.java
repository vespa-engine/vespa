// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.jdk8compat;

import java.util.function.IntFunction;

/**
 * Backport of new {@link java.util.Collection} methods added after JDK8
 *
 * @author bjorncs
 */
public interface Collection {
    static <T> T[] toArray(java.util.Collection<T> collection, IntFunction<T[]> generator) {
        return collection.toArray(generator.apply(collection.size()));
    }

}
