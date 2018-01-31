// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.client;


import com.yahoo.vespa.http.client.config.SessionParams;
import com.yahoo.vespa.http.client.core.api.FeedClientImpl;

import static com.yahoo.vespa.http.client.SessionFactory.createTimeoutExecutor;

/**
 * Factory for creating FeedClient.
 *
 * @author dybis
 */
public class FeedClientFactory {

    /**
     * Creates a FeedClient.
     *
     * @param sessionParams parameters for connection, hosts, cluster configurations and more.
     * @param resultCallback on each result, this callback is called.
     * @return newly created FeedClient API object.
     */
    public static FeedClient create(SessionParams sessionParams, FeedClient.ResultCallback resultCallback) {
        return new FeedClientImpl(sessionParams, resultCallback, createTimeoutExecutor());
    }

}
