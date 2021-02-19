// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.processing.test.documentation;

import com.google.common.util.concurrent.MoreExecutors;
import com.yahoo.component.chain.Chain;
import com.yahoo.processing.*;
import com.yahoo.processing.execution.*;

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
        response.data().complete().addListener(new RunnableExecution(request,
                new ExecutionWithResponse(asyncChain, response, execution)),
                MoreExecutors.directExecutor());
        return response;
    }

}
