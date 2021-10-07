// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.jaxrs.client;

import java.io.IOException;
import java.util.function.Function;

/**
 * A {@link JaxRsStrategy} that does not use the network, only forwards calls to a local object.
 *
 * @author bakksjo
 */
public class LocalPassThroughJaxRsStrategy<T> implements JaxRsStrategy<T> {
    private final T api;

    public LocalPassThroughJaxRsStrategy(final T api) {
        this.api = api;
    }

    @Override
    public <R> R apply(final Function<T, R> function) throws IOException {
        return function.apply(api);
    }
}
