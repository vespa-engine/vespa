// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.jaxrs.client;

import com.yahoo.vespa.applicationmodel.HostName;

import javax.ws.rs.ProcessingException;
import java.io.IOException;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A {@link JaxRsStrategy} that will retry on failures, looping twice over all available server hosts before giving up.
 *
 * @author bakksjo
 */
public class RetryingJaxRsStrategy<T> implements JaxRsStrategy<T> {
    private static final Logger logger = Logger.getLogger(RetryingJaxRsStrategy.class.getName());
    private static final int NUM_LOOP_ATTEMPTS = 2;

    private final Set<HostName> hostNames;
    private final int port;
    private final JaxRsClientFactory jaxRsClientFactory;
    private final Class<T> apiClass;
    private String pathPrefix;

    public RetryingJaxRsStrategy(
            final Set<HostName> hostNames,
            final int port,
            final JaxRsClientFactory jaxRsClientFactory,
            final Class<T> apiClass,
            final String pathPrefix) {
        if (hostNames.isEmpty()) {
            throw new IllegalArgumentException("hostNames argument must not be empty");
        }
        Objects.requireNonNull(jaxRsClientFactory, "jaxRsClientFactory argument may not be null");
        Objects.requireNonNull(apiClass, "apiClass argument may not be null");
        Objects.requireNonNull(pathPrefix, "pathPrefix argument may not be null");
        this.hostNames = hostNames;
        this.port = port;
        this.jaxRsClientFactory = jaxRsClientFactory;
        this.apiClass = apiClass;
        this.pathPrefix = pathPrefix;
    }

    @Override
    public <R> R apply(final Function<T, R> function) throws IOException {
        ProcessingException sampleException = null;

        for (int i = 0; i < NUM_LOOP_ATTEMPTS; ++i) {
            for (final HostName hostName : hostNames) {
                final T jaxRsClient = jaxRsClientFactory.createClient(apiClass, hostName, port, pathPrefix);
                try {
                    return function.apply(jaxRsClient);
                } catch (ProcessingException e) {
                    sampleException = e;
                    logger.log(Level.INFO, "Failed REST API call to "
                            + hostName + ":" + port + pathPrefix + " (in retry loop): "
                            + e.getMessage());
                }
            }
        }

        final String message = String.format(
                "Giving up invoking REST API after %d tries against hosts %s.%s",
                NUM_LOOP_ATTEMPTS,
                hostNames,
                sampleException == null ? "" : ", sample error: " + sampleException.getMessage());

        assert sampleException != null;
        throw new IOException(message, sampleException);
    }
}
