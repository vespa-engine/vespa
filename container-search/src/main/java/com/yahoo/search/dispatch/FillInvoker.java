// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.dispatch;

import ai.vespa.telemetry.api.trace.OtelTracing;
import ai.vespa.telemetry.api.trace.TraceAttributes;
import com.yahoo.search.Result;
import com.yahoo.search.result.ErrorMessage;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;


/**
 * FillInvoker encapsulates an allocated connection for running a document summary retrieval.
 * The invocation object can be stateful and should not be reused.
 *
 * @author ollivir
 */
public abstract class FillInvoker extends CloseableInvoker {

    /** Retrieves document summaries for the unfilled hits in the given {@link Result} */
    public void fill(Result result, String summaryClass) {
        OtelTracing.instrument("dispatch.fill", () -> {
            sendFillRequest(result, summaryClass);
            getFillResults(result, summaryClass);

            // Fill failures are RETURNED, not thrown: every failure path in RpcProtobufFillInvoker adds an
            // ErrorMessage to the result and returns normally, so inSpan's own catch never sees them and the
            // span would be reported as successful. This result is the partition Result built fresh in
            // VespaBackend.partitionHits, so an error on it can only have come from this fill.
            //
            // Only the SHORT message is used. The detailed half is caller-supplied and can carry the request
            // URI, and with it the query text, which must never reach a span.
            //
            // Deliberately NOT setFinalStatus(): unlike SearchInvoker.search, this method never reported one,
            // and adding tracing must not change what the teardown callback records.
            ErrorMessage error = result.hits().getError();
            if (error != null)
                Span.current().setStatus(StatusCode.ERROR)
                              .setAttribute(TraceAttributes.ERROR_CODE, error.getCode())
                              .setAttribute(TraceAttributes.ERROR_MESSAGE, error.getMessage());
        });
    }

    protected abstract void getFillResults(Result result, String summaryClass);

    protected abstract void sendFillRequest(Result result, String summaryClass);

}
