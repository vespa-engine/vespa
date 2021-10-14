// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.jaxrs.client;

import com.yahoo.vespa.applicationmodel.HostName;

import javax.ws.rs.ProcessingException;
import java.io.IOException;
import java.util.Objects;
import java.util.function.Function;

/**
 * A {@link JaxRsStrategy} that will try API calls once against a single server, giving up immediately on failure.
 *
 * @author bakksjo
 */
public class NoRetryJaxRsStrategy<T> implements JaxRsStrategy<T> {
    private final HostName hostName;
    private final int port;
    private final JaxRsClientFactory jaxRsClientFactory;
    private final Class<T> apiClass;
    private final String scheme;
    private String pathPrefix;

    public NoRetryJaxRsStrategy(
            final HostName hostName,
            final int port,
            final JaxRsClientFactory jaxRsClientFactory,
            final Class<T> apiClass,
            final String pathPrefix,
            String scheme) {
        Objects.requireNonNull(hostName, "hostName argument may not be null");
        Objects.requireNonNull(jaxRsClientFactory, "jaxRsClientFactory argument may not be null");
        Objects.requireNonNull(apiClass, "apiClass argument may not be null");
        Objects.requireNonNull(pathPrefix, "pathPrefix argument may not be null");
        this.hostName = hostName;
        this.port = port;
        this.jaxRsClientFactory = jaxRsClientFactory;
        this.apiClass = apiClass;
        this.pathPrefix = pathPrefix;
        this.scheme = scheme;
    }

    @Override
    public <R> R apply(final Function<T, R> function) throws IOException {
        final T jaxRsClient = jaxRsClientFactory.createClient(apiClass, hostName, port, pathPrefix, scheme);
        try {
            return function.apply(jaxRsClient);
        } catch (ProcessingException e) {
            throw new IOException("Communication with REST server failed", e);
        }
    }
}
