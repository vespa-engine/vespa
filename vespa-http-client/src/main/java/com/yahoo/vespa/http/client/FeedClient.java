// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.client;

import com.yahoo.vespa.http.client.core.JsonReader;
import com.yahoo.vespa.http.client.core.XmlFeedReader;

import java.io.InputStream;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * API for feeding document operations (add, removes or updates) to one or many Vespa clusters.
 * Use the factory to configure and set up an instance of this API.
 * The feedclient does automatically error recovery and reconnects to hosts when
 * connections die.
 *
 * A {@link FeedClientFactory} is provided to instantiate Sessions.
 *
 * @author dybis
 * @see FeedClientFactory
 * @since 5.29.0
 */
public interface FeedClient extends AutoCloseable {

    /**
     * Streams a document to cluster(s). If the pipeline and buffers are full, this call will be blocking.
     * Documents might time out before they are sent. Failed documents are not retried.
     * Don't call stream() after close is called.
     * @param documentId Document id of the document.
     * @param documentData The document data as JSON or XML (as specified when using the factory to create the API)
     */
    void stream(String documentId, CharSequence documentData);

    /**
     * Streams a document to cluster(s). If the pipeline and buffers are full, this call will be blocking.
     * Documents might time out before they are sent. Failed documents are not retried.
     * Don't call stream() after close is called.
     * @param documentId Document id of the document.
     * @param documentData The document data as JSON or XML (as specified when using the factory to create the API)
     * @param context Any context, will be accessible in the result of the callback.
     */
    void stream(String documentId, CharSequence documentData, Object context);


    /**
     * This callback is executed when new results are arriving. Don't do any heavy lifting in this thread (no IO, disk,
     * or heavy CPU usage). This call back will run in a different thread than your main program so use e.g.
     * AtomicInteger for counters and follow general guides for thread-safe programming.
     * There is an example implementation in class SimpleLoggerResultCallback.
     */
    static interface ResultCallback {
        void onCompletion(String docId, Result documentResult);
    }

    /**
     * Waits for all results to arrive and closes the FeedClient. Don't call any other method after calling close().
     * Does not throw any exceptions.
     */
    @Override
    void close();

    /**
     * Returns stats about the cluster.
     * @return JSON string with information about cluster.
     */
    public String getStatsAsJson();

    /**
     * Utility function that takes an array of JSON documents and calls the FeedClient for each element.
     * @param inputStream This can be a very large stream. The outer element is an array (of document operations).
     * @param feedClient The feedClient that will receive the document operations.
     * @param numSent increased per document sent to API (but no waiting for results).
     */
    public static void feedJson(InputStream inputStream, FeedClient feedClient, AtomicInteger numSent) {
        JsonReader.read(inputStream, feedClient, numSent);
    }

    /**
     * Utility function that takes an array of XML documents and calls the FeedClient for each element.
     * The XML document has to be formatted with line space on each line (like "regular" XML, but stricter
     * than the specifications of XML).
     * @param inputStream This can be a very large stream.
     * @param feedClient The feedClient that will receive the document operations.
     * @param numSent increased per document sent to API (but no waiting for results).
     */
    public static void feedXml(InputStream inputStream, FeedClient feedClient, AtomicInteger numSent) {
        try {
            XmlFeedReader.read(inputStream, feedClient, numSent);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
