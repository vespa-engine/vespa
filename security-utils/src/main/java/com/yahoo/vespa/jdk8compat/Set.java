// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.jdk8compat;

import java.util.Arrays;
import java.util.HashSet;

/**
 * Backport of new {@link java.util.Set} methods added after JDK8
 *
 * @author bjorncs
 */
public interface Set {
    @SafeVarargs
    @SuppressWarnings("varargs")
    static <E> java.util.Set<E> of(E... elements) {
        return new HashSet<>(Arrays.asList(elements));
    }
}
