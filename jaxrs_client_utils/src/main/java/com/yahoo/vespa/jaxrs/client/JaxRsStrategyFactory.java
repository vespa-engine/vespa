// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.jaxrs.client;

import com.yahoo.vespa.applicationmodel.HostName;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.Set;

/**
 * The idea behind this class is twofold:
 *
 * <ol>
 *     <li>
 *         It can provide alternative strategies for communicating with a JAX-RS-based server API.
 *     </li>
 *     <li>
 *         It can make it simpler to work with hosts that serve multiple APIs. Example:
 *         <pre>{@code
 *             final JaxRsStrategyFactory apiFactory = new JaxRsStrategyFactory(hostNames, port, clientFactory);
 *             // No need to repeat the hostNames etc here:
 *             apiFactory.apiWithRetries(FooApi.class).apply(FooApi::fooMethod);
 *             apiFactory.apiWithRetries(BarApi.class).apply(BarApi::barMethod);
 *             apiFactory.apiWithRetries(BazongaApi.class).apply(BazongaApi::bazinga);
 *         }</pre>
 *     </li>
 * </ol>
 *
 * @author bakksjo
 */
public class JaxRsStrategyFactory {
    private final Set<HostName> hostNames;
    private int port;
    private final String scheme;
    private final JaxRsClientFactory jaxRsClientFactory;

    // TODO: We might need to support per-host port specification.
    public JaxRsStrategyFactory(
            final Set<HostName> hostNames,
            final int port,
            final JaxRsClientFactory jaxRsClientFactory,
            String scheme) {
        if (hostNames.isEmpty()) {
            throw new IllegalArgumentException("hostNames argument must not be empty");
        }
        Objects.requireNonNull(jaxRsClientFactory, "jaxRsClientFactory argument may not be null");
        this.hostNames = hostNames;
        this.port = port;
        this.jaxRsClientFactory = jaxRsClientFactory;
        this.scheme = scheme;
    }

    public <T> RetryingJaxRsStrategy<T> apiWithRetries(final Class<T> apiClass, final String pathPrefix) {
        Objects.requireNonNull(apiClass, "apiClass argument may not be null");
        Objects.requireNonNull(pathPrefix, "pathPrefix argument may not be null");
        return new RetryingJaxRsStrategy<T>(hostNames, port, jaxRsClientFactory, apiClass, pathPrefix, scheme);
    }

    public <T> JaxRsStrategy<T> apiNoRetries(final Class<T> apiClass, final String pathPrefix) {
        Objects.requireNonNull(apiClass, "apiClass argument may not be null");
        Objects.requireNonNull(pathPrefix, "pathPrefix argument may not be null");
        final HostName hostName = getRandom(hostNames);
        return new NoRetryJaxRsStrategy<T>(hostName, port, jaxRsClientFactory, apiClass, pathPrefix, scheme);
    }

    private static final Random random = new Random();

    private static <T> T getRandom(final Collection<? extends T> collection) {
        int index = random.nextInt(collection.size());
        return getIndex(collection, index);
    }

    private static <T> T getIndex(final Collection<? extends T> collection, final int index) {
        if (index >= collection.size() || index < 0) {
            throw new IndexOutOfBoundsException(
                    "Attempt to get element #" + index + " from collection with " + collection.size() + " elements");
        }

        if (collection instanceof List) {
            final List<? extends T> list = (List<? extends T>) collection;
            return list.get(index);
        }

        final Iterator<? extends T> iterator = collection.iterator();
        for (int i = 0; i < index; i++) {
            iterator.next();
        }
        return iterator.next();
    }
}
