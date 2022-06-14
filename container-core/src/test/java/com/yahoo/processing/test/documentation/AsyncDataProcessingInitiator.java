// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.processing.test.documentation;

import com.yahoo.component.chain.Chain;
import com.yahoo.processing.Processor;
import com.yahoo.processing.Request;
import com.yahoo.processing.Response;
import com.yahoo.processing.execution.Execution;
import com.yahoo.processing.execution.ExecutionWithResponse;
import com.yahoo.processing.execution.RunnableExecution;

/**
 * A processor which registers a listener on the future completion of
 * asynchronously arriving data to perform another chain at that point.
 */
public class AsyncDataProcessingInitiator extends Processor {

    private final Chain<Processor> asyncChain;

    public AsyncDataProcessingInitiator(Chain<Processor> asyncChain) {
        this.asyncChain=asyncChain;
    }

    @Override
    public Response process(Request request, Execution execution) {
        Response response=execution.process(request);
        response.data().completeFuture().whenComplete((__, ___) -> new RunnableExecution(request,
                new ExecutionWithResponse(asyncChain, response, execution)).run());
        return response;
    }

}
