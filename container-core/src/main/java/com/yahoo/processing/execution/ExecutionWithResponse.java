// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.processing.execution;

import com.yahoo.component.chain.Chain;
import com.yahoo.processing.Processor;
import com.yahoo.processing.Request;
import com.yahoo.processing.Response;

/**
 * An execution which has a response which is returned when this gets to the end of the chain.
 * This is useful to run processing chains where a response exists up front, typically for on completion listeners.
 *
 * @author bratseth
 */
public class ExecutionWithResponse extends Execution {

    private Response response;

    /**
     * Creates an execution which will return a given response at the end of the chain.
     *
     * @param chain     the chain to execute in this
     * @param response  the response this will return from {@link #process} then the end of this chain is reached
     * @param execution the the parent of this execution
     */
    public ExecutionWithResponse(Chain<? extends Processor> chain, Response response, Execution execution) {
        super(chain, execution);
        this.response = response;
    }

    @Override
    protected Response defaultResponse(Request request) {
        return response;
    }

}
