// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.jaxrs.client;

import java.io.IOException;
import java.util.function.Function;

/**
 * This interface allows different strategies for accessing server-side JAX-RS APIs programmatically.
 *
 * @author bakksjo
 */
public interface JaxRsStrategy<T> {
    <R> R apply(final Function<T, R> function) throws IOException;

    default <R> R apply(final Function<T, R> function, JaxRsTimeouts timeouts) throws IOException {
        return apply(function);
    }
}
