// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.jdk8compat;

import java.util.Arrays;

/**
 * Backport of new {@link java.util.List} methods added after JDK8
 *
 * @author bjorncs
 */
public interface List {
    @SafeVarargs
    @SuppressWarnings("varargs")
    static <E> java.util.List<E> of(E... elements) {
        return Arrays.asList(elements);
    }
}
