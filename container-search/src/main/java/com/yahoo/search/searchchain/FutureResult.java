// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.searchchain;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.yahoo.yolean.Exceptions;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.result.ErrorMessage;

/**
 * Extends a {@code FutureTask<Result>}, with some added error handling
 */
public class FutureResult extends FutureTask<Result> {

    private final Query query;

    /** Only used for generating messages */
    private final Execution execution;

    private final static Logger log = Logger.getLogger(FutureResult.class.getName());

    FutureResult(Callable<Result> callable, Execution execution, final Query query) {
        super(callable);
        this.query = query;
        this.execution = execution;
    }

    @Override
    public Result get() {
        Result result;
        try {
            result = super.get();
        }
        catch (InterruptedException e) {
            result = new Result(getQuery(), ErrorMessage.createUnspecifiedError(
                    "'" + execution + "' was interrupted while executing: " + Exceptions.toMessageString(e)));
        }
        catch (ExecutionException e) {
            log.log(Level.WARNING,"Exception on executing " + execution + " for " + query,e);
            result = new Result(getQuery(), ErrorMessage.createErrorInPluginSearcher(
                    "Error in '" + execution + "': " + Exceptions.toMessageString(e),
                    e.getCause()));
        }
        return result;
    }

    @Override
    public Result get(long timeout, TimeUnit timeunit) {
        Result result;
        try {
            result = super.get(timeout, timeunit);
        }
        catch (InterruptedException e) {
            result = new Result(getQuery(), ErrorMessage.createUnspecifiedError(
                    "'" + execution + "' was interrupted while executing: " + Exceptions.toMessageString(e)));
        }
        catch (ExecutionException e) {
            log.log(Level.WARNING,"Exception on executing " + execution + " for " + query, e);
            result = new Result(getQuery(), ErrorMessage.createErrorInPluginSearcher(
                    "Error in '" + execution + "': " + Exceptions.toMessageString(e),
                    e.getCause()));
        }
        catch (TimeoutException e) {
            result = new Result(getQuery(), createTimeoutError());
        }
        return result;
    }

    /** Returns the query used in this execution, never null */
    public Query getQuery() {
        return query;
    }

    ErrorMessage createTimeoutError() {
        return ErrorMessage.createTimeout(
                "Error executing '" + execution + "': " + " Chain timed out.");

    }
}
