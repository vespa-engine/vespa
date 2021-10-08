// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.client;

import java.io.OutputStream;
import java.util.concurrent.BlockingQueue;

/**
 * A Session is an entity used to feed operations (like documents, removes or updates) to one Vespa
 * cluster or several clusters in parallel. Current implementations are fail-fast, i.e. all feeding
 * errors are propagated to the user as quickly as possible and with as much detail as possible.
 *
 * Implementations of this interface are required to be thread safe.
 *
 * A {@link SessionFactory} is provided to instantiate Sessions.
 *
 * @author Einar M R Rosenvinge
 * @see SessionFactory
 * @deprecated use either FeedClient or SyncFeedClient // TODO: Remove on Vespa 8
 */
@Deprecated
public interface Session extends AutoCloseable {

    /**
     * Returns an OutputStream that can be used to write ONE operation, identified by the
     * given document ID. The data format must match the
     * {@link com.yahoo.vespa.http.client.config.FeedParams.DataFormat} given when this
     * Session was instantiated. Note that most data formats include the document ID in the
     * actual buffer, which <em>must</em> match the document ID given as a parameter to this
     * method. It is (as always) important to close the OutputStream returned - nothing
     * is written to the wire until this is done. Note also that the Session holds a certain,
     * dynamically determined maximum number of document operations in memory.
     * When this threshold is reached, {@link java.io.OutputStream#close()} will block.
     *
     *
     * @param documentId the unique ID identifying this operation in the system
     * @return an OutputStream to write the operation payload into
     */
    OutputStream stream(CharSequence documentId);

    /**
     * Returns {@link Result}s for all operations enqueued by {@link #stream(CharSequence)}.
     * Note that the order of results is non-deterministic, with <em>one</em> exception - results
     * for one document ID are returned in the order they were enqueued. In all other cases
     * Results may appear out-of-order.
     *
     * @return a blocking queue for retrieving results
     * @see Result
     */
    BlockingQueue<Result> results();

    /**
     * Closes this Session. All resources are freed, persistent connections are closed and
     * internal threads are stopped.
     *
     * @throws RuntimeException in cases where underlying resources throw on shutdown/close
     */
    void close();

    /**
     * Returns stats about the cluster.
     * @return JSON string with information about cluster.
     */
    String getStatsAsJson();

}
