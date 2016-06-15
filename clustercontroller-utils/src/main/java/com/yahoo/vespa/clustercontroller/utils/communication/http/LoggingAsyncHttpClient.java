// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.utils.communication.http;

import com.yahoo.vespa.clustercontroller.utils.communication.async.AsyncOperation;
import com.yahoo.vespa.clustercontroller.utils.communication.async.PipedAsyncOperation;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.logging.Level;
import java.util.logging.Logger;

public class LoggingAsyncHttpClient<T extends HttpResult> extends AsyncHttpClientWithBase<T> {
    private static final Logger log = Logger.getLogger(LoggingAsyncHttpClient.class.getName());
    private int requestCounter = 0;

    public LoggingAsyncHttpClient(AsyncHttpClient<T> client) {
        super(client);
        log.info("Logging HTTP requests if fine logging level is added");
    }

    public AsyncOperation<T> execute(HttpRequest r) {
        final int requestCount = ++requestCounter;
        log.fine("Issuing HTTP request " + requestCount + ": " + r.toString(true));
        final AsyncOperation<T> op = client.execute(r);
        return new PipedAsyncOperation<T, T>(op) {
            @Override
            public T convertResult(T result) {
                if (log.isLoggable(Level.FINE)) {
                    if (op.isSuccess()) {
                        log.fine("HTTP request " + requestCount + " completed: " + result.toString(true));
                    } else {
                        StringWriter sw = new StringWriter();
                        op.getCause().printStackTrace(new PrintWriter(sw));
                        log.fine("HTTP request " + requestCount + " failed: " + sw);
                    }
                }
                return result;
            }
        };
    }
}
