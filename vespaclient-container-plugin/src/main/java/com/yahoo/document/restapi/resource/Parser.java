// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.restapi.resource;

import com.yahoo.yolean.Exceptions;

import java.util.function.Function;

/**
 * Unchecked exception-wrapping for parser invocations.
 *
 * @author Jon Marius Venstad
 */
@FunctionalInterface
interface Parser<T> extends Function<String, T> {
    default T parse(String value) {
        try {
            return apply(value);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Failed parsing '" + value + "': " + Exceptions.toMessageString(e));
        }
    }
}

