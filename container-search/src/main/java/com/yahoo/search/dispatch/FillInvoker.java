// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.dispatch;

import com.yahoo.search.Result;

/**
 * FillInvoker encapsulates an allocated connection for running a document summary retrieval.
 * The invocation object can be stateful and should not be reused.
 *
 * @author ollivir
 */
public abstract class FillInvoker extends CloseableInvoker {

    /** Retrieves document summaries for the unfilled hits in the given {@link Result} */
    public void fill(Result result, String summaryClass) {
        sendFillRequest(result, summaryClass);
        getFillResults(result, summaryClass);
    }

    protected abstract void getFillResults(Result result, String summaryClass);

    protected abstract void sendFillRequest(Result result, String summaryClass);

}
