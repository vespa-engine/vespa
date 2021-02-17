// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.processing.execution;

import com.yahoo.processing.Request;
import com.yahoo.processing.Response;

/**
 * An adaptor of an Execution to a runnable. Calling run on this causes process to be called on the
 * given processor.
 *
 * @author bratseth
 */
public class RunnableExecution implements Runnable {

    private final Request request;
    private final Execution execution;
    private Response response = null;
    private Throwable exception = null;

    public RunnableExecution(Request request, Execution execution) {
        this.request = request;
        this.execution = execution;
    }

    /**
     * Calls process on the execution of this.
     * This will result in either response or exception being set on this.
     * Calling this never throws an exception.
     */
    public void run() {
        try {
            response = execution.process(request);
        } catch (Exception e) {
            exception = e; // TODO: Log
        }
    }

    /**
     * Returns the response from executing this, or null if exception is set or run() has not been called yet
     */
    public Response getResponse() {
        return response;
    }

    /**
     * Returns the exception from executing this, or null if response is set or run() has not been called yet
     */
    public Throwable getException() {
        return exception;
    }

}
