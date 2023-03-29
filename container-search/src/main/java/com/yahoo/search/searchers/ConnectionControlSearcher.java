// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.searchers;

import com.yahoo.component.annotation.Inject;
import com.yahoo.processing.request.CompoundName;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.searchchain.Execution;

import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/**
 * Searcher which can enforce HTTP connection close based on query properties.
 *
 * <p>
 * This searcher informs the client to close a persistent HTTP connection if the
 * connection is older than the configured max lifetime. This is done by adding
 * the "Connection" HTTP header with the value "Close" to the result.
 * </p>
 *
 * <p>
 * The searcher reads the query property "connectioncontrol.maxlifetime", which
 * is an integer number of seconds, to get the value for maximum connection
 * lifetime. Setting it to zero will enforce connection close independently of
 * the age of the connection. Typical usage would be as follows:
 * </p>
 *
 * <ol>
 * <li>Add the ConnectionControlSearcher to the default search chain of your
 * application. (It has no special ordering considerations.)</li>
 *
 * <li>For the default query profile of your application, set a reasonable value
 * for "connectioncontrol.maxlifetime". The definition of reasonable will be
 * highly application dependent, but it should always be less than the grace
 * period when taking the container out of production traffic.</li>
 *
 * <li>Deploy application. The container will now inform clients to close
 * connections/reconnect within the configured time limit.
 * </ol>
 *
 * @author frodelu
 * @author Steinar Knutsen
 */
public class ConnectionControlSearcher extends Searcher {

    private final String simpleName = this.getClass().getSimpleName();

    private final LongSupplier clock;

    private static final CompoundName KEEPALIVE_MAXLIFETIMESECONDS = CompoundName.from("connectioncontrol.maxlifetime");
    private static final String HTTP_CONNECTION_HEADER_NAME = "Connection";
    private static final String HTTP_CONNECTION_CLOSE_ARGUMENT = "Close";

    @Inject
    public ConnectionControlSearcher() {
        this(() -> System.currentTimeMillis());
    }

    private ConnectionControlSearcher(LongSupplier clock) {
        this.clock = clock;
    }

    /**
     * Create a searcher instance suitable for unit tests.
     *
     * @param clock a simulated or real clock behaving similarly to System.currentTimeMillis()
     * @return a fully initialised instance
     */
    public static ConnectionControlSearcher createTestInstance(LongSupplier clock) {
        return new ConnectionControlSearcher(clock);
    }

    @Override
    public Result search(Query query, Execution execution) {
        Result result = execution.search(query);

        query.trace(false, 3, simpleName, " updating headers.");
        keepAliveProcessing(query, result);
        return result;
    }

    /**
     * If the HTTP connection has been alive for too long, set the header
     * "Connection: Close" to tell the client to close the connection after this
     * request.
     */
    private void keepAliveProcessing(Query query, Result result) {
        int maxLifetimeSeconds = query.properties().getInteger(KEEPALIVE_MAXLIFETIMESECONDS, -1);

        if (maxLifetimeSeconds < 0) {
            return;
        } else if (maxLifetimeSeconds == 0) {
            result.getHeaders(true).put(HTTP_CONNECTION_HEADER_NAME, HTTP_CONNECTION_CLOSE_ARGUMENT);
            query.trace(false, 5, simpleName, ": Max HTTP connection lifetime set to 0; adding \"", HTTP_CONNECTION_HEADER_NAME,
                    ": ", HTTP_CONNECTION_CLOSE_ARGUMENT, "\" header");
        } else {
            setCloseIfLifetimeExceeded(query, result, maxLifetimeSeconds);
        }
    }

    private void setCloseIfLifetimeExceeded(Query query, Result result, int maxLifetimeSeconds) {
        if (query.getHttpRequest() == null) {
            query.trace(false, 5, simpleName, " got max lifetime = ", maxLifetimeSeconds,
                    ", but got no JDisc request. Setting no header.");
            return;
        }

        final long connectedAtMillis = query.getHttpRequest().getConnectedAt(TimeUnit.MILLISECONDS);
        final long maxLifeTimeMillis = maxLifetimeSeconds * 1000L;
        if (connectedAtMillis + maxLifeTimeMillis < clock.getAsLong()) {
            result.getHeaders(true).put(HTTP_CONNECTION_HEADER_NAME, HTTP_CONNECTION_CLOSE_ARGUMENT);
            query.trace(false, 5, simpleName, ": Max HTTP connection lifetime (", maxLifetimeSeconds, ") exceeded; adding \"",
                    HTTP_CONNECTION_HEADER_NAME, ": ", HTTP_CONNECTION_CLOSE_ARGUMENT, "\" header");
        }
    }

}
