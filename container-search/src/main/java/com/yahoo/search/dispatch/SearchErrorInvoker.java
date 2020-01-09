// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.dispatch;

import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.result.Coverage;
import com.yahoo.search.result.ErrorMessage;
import com.yahoo.search.searchchain.Execution;

import java.io.IOException;
import java.util.Optional;

/**
 * A search invoker that will immediately produce an error that occurred during
 * invoker construction. Currently used for invalid searchpath values and node
 * failure
 *
 * @author ollivir
 */
public class SearchErrorInvoker extends SearchInvoker {

    private final ErrorMessage message;
    private Query query;
    private final Coverage coverage;
    private ResponseMonitor<SearchInvoker> monitor;

    public SearchErrorInvoker(ErrorMessage message, Coverage coverage) {
        super(Optional.empty());
        this.message = message;
        this.coverage = coverage;
    }

    public SearchErrorInvoker(ErrorMessage message) {
        this(message, null);
    }

    @Override
    protected void sendSearchRequest(Query query) throws IOException {
        this.query = query;
        if(monitor != null) {
            monitor.responseAvailable(this);
        }
    }

    @Override
    protected InvokerResult getSearchResult(Execution execution) throws IOException {
        Result res = new Result(query, message);
        if (coverage != null) {
            res.setCoverage(coverage);
        }
        return new InvokerResult(res);
    }

    @Override
    protected void release() {
        // nothing to do
    }

    @Override
    protected void setMonitor(ResponseMonitor<SearchInvoker> monitor) {
        this.monitor = monitor;
    }
}
