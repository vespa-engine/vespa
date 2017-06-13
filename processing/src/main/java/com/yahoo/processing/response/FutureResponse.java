// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.processing.response;

import com.google.common.util.concurrent.ForwardingFuture;
import com.google.common.util.concurrent.ListenableFutureTask;
import com.yahoo.processing.Request;
import com.yahoo.processing.Response;
import com.yahoo.processing.execution.Execution;
import com.yahoo.processing.request.ErrorMessage;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A processing response which will arrive in the future.
 *
 * @author bratseth
 */
public class FutureResponse extends ForwardingFuture<Response> {

    private final Request request;

    /**
     * Only used for generating messages
     */
    private final Execution execution;

    private final static Logger log = Logger.getLogger(FutureResponse.class.getName());

    private final ListenableFutureTask<Response> futureTask;

    public FutureResponse(final Callable<Response> callable, Execution execution, final Request request) {
        this.futureTask = ListenableFutureTask.create(callable);
        this.request = request;
        this.execution = execution;
    }

    @Override
    public ListenableFutureTask<Response> delegate() {
        return futureTask;
    }

    public
    @Override
    Response get() {
        try {
            return super.get();
        } catch (InterruptedException e) {
            return new Response(request, new ErrorMessage("'" + execution + "' was interrupted", e));
        } catch (ExecutionException e) {
            log.log(Level.WARNING, "Exception on executing " + execution + " for " + request, e);
            return new Response(request, new ErrorMessage("Error in '" + execution + "'", e));
        }
    }

    public
    @Override
    Response get(long timeout, TimeUnit timeunit) {
        try {
            return super.get(timeout, timeunit);
        } catch (InterruptedException e) {
            return new Response(request, new ErrorMessage("'" + execution + "' was interrupted", e));
        } catch (ExecutionException e) {
            log.log(Level.WARNING, "Exception on executing " + execution + " for " + request, e);
            return new Response(request, new ErrorMessage("Error in '" + execution + "'", e));
        } catch (TimeoutException e) {
            return new Response(request, new ErrorMessage("Error executing '" + execution + "': " + " Chain timed out."));
        }
    }

    /**
     * Returns the query used in this execution, never null
     */
    public Request getRequest() {
        return request;
    }

}
