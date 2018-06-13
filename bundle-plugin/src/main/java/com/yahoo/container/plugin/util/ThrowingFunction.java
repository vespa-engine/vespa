// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.plugin.util;

/* Equivalent to java.util.function.Function, but allows throwing of Exceptions */

/**
 * @author ollivir
 */
public interface ThrowingFunction<T, U> {
    U apply(T input) throws Exception;
}
