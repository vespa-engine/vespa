// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.client;

import com.yahoo.vespa.http.client.core.JsonReader;
import com.yahoo.vespa.http.client.core.XmlFeedReader;

import java.io.InputStream;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * API for feeding document operations (add, removes or updates) to one or many Vespa clusters.
 * Use the factory to configure and set up an instance of this. Instances are expensive - create one instance of this
 * and use it for all feed operations (from multiple threads, if desired) for the duration of your client runtime.
 * The feedclient does automatic error recovery and reconnects to hosts when connections die.
 *
 * A {@link FeedClientFactory} is provided to instantiate Sessions.
 *
 * See com.yahoo.text.Text.stripInvalidCharacters(String) to remove invalid characters from string fields before feeding
 *
 * Instances of this are multithread safe.
 *
 * @author dybis
 * @see FeedClientFactory
 */
public interface FeedClient extends AutoCloseable {

    /**
     * Issues a document operation to the configured cluster(s).
     * If the pipeline and buffers are full, this call will be blocking, ensuring that operations are not
     * produced faster than the can be handled. Transient failured are retried internally by this client.
     * Exactly one callback will always be received for each (completed) call to this.
     *
     * @param documentId the document id of the document
     * @param documentData the document data as JSON or XML (as specified when using the factory to create the API)
     */
    default void stream(String documentId, CharSequence documentData) {
        stream(documentId, documentData, null);
    }

    /**
     * Issues a document operation to the configured cluster(s).
     * If the pipeline and buffers are full, this call will be blocking, ensuring that operations are not
     * produced faster than the can be handled. Transient failured are retried internally by this client.
     * Exactly one callback will always be received for each (completed) call to this.
     *
     * @param documentId the document id of the document
     * @param documentData the document data as JSON or XML (as specified when using the factory to create the API)
     * @param context a context object which will be accessible in the result of the callback, or null if none
     */
    default void stream(String documentId, CharSequence documentData, Object context) {
        stream(documentId, null, documentData, context);
    }

    /**
     * Issues a document operation to the configured cluster(s).
     * If the pipeline and buffers are full, this call will be blocking, ensuring that operations are not
     * produced faster than the can be handled. Transient failures are retried internally by this client.
     * Exactly one callback will always be received for each (completed) call to this.
     *
     * @param documentId the document id of the document
     * @param operationId the id to use for this operation, or null to let the client decide an operation id.
     *                    This id must be unique for every operation. Passing the operation id allows clients
     *                    to prepare to receive a response for it before issuing the operation to the client.
     * @param documentData the document data as JSON or XML (as specified when using the factory to create the API)
     * @param context a context object which will be accessible in the result of the callback, or null if none
     */
    void stream(String documentId, String operationId, CharSequence documentData, Object context);

    /**
     * Waits for all results to arrive and closes the FeedClient. Don't call any other method after calling close().
     * Does not throw any exceptions.
     */
    @Override
    void close();

    /**
     * Returns stats about the cluster
     * 
     * @return JSON string with information about cluster
     */
    String getStatsAsJson();

    /**
     * Utility function that takes an array of JSON documents and calls the FeedClient for each element.
     *
     * @param inputStream the stream to feed. This can be a very large stream.
     *                    The outer element must be an array of document operations.
     * @param feedClient the feed client that will receive the document operations
     * @param numSent increased per document sent to API (but not waiting for results)
     */
    static void feedJson(InputStream inputStream, FeedClient feedClient, AtomicInteger numSent) {
        JsonReader.read(inputStream, feedClient, numSent);
    }

    /**
     * Utility function that takes an array of XML documents and calls the FeedClient for each element.
     * The XML document has to be formatted with line space on each line (like "regular" XML, but stricter
     * than the specifications of XML).
     *
     * @param inputStream the stream to feed. This can be a very large stream. Operations must be enclosed in a
     *                    top-level &lt;vespafeed&gt; tag
     * @param feedClient the feed client that will receive the document operations
     * @param numSent increased per document sent to API (but not waiting for results)
     */
    static void feedXml(InputStream inputStream, FeedClient feedClient, AtomicInteger numSent) {
        try {
            XmlFeedReader.read(inputStream, feedClient, numSent);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * This callback is executed when new results are arriving or an error occur.
     * Don't do any heavy lifting in this thread (no IO, disk, or heavy CPU usage).
     * This call back will run in a different thread than your main program so use e.g.
     * AtomicInteger for counters and follow general guides for thread-safe programming.
     * There is an example implementation in class SimpleLoggerResultCallback.
     */
    interface ResultCallback {

        /**
         * This callback is always called exactly once for each feed operation passed to the client
         * instance, whether or not it was successful.
         */
        void onCompletion(String docId, Result documentResult);

        /**
         * Called with an exception whenever an endpoint specific error occurs during feeding.
         * The error may or may not be transient - the operation will in both cases be retried until it's successful.
         * This callback is intended for application level monitoring (logging, metrics, altering etc).
         * Document specific errors will be reported back through {@link #onCompletion(String, Result)}.
         *
         * @see FeedEndpointException
         * @param exception an exception specifying endpoint and cause. See {@link FeedEndpointException} for details.
         */
        default void onEndpointException(FeedEndpointException exception) {}

    }

}
