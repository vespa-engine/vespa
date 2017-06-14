// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.processing.test.documentation;

import com.yahoo.component.chain.Chain;
import com.yahoo.processing.Processor;
import com.yahoo.processing.Request;
import com.yahoo.processing.Response;
import com.yahoo.processing.execution.AsyncExecution;
import com.yahoo.processing.execution.Execution;
import com.yahoo.processing.response.FutureResponse;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Call a number of chains in parallel
 */
public class Federator extends Processor {

    private final List<Chain<? extends Processor>> chains;

    @SafeVarargs
    @SuppressWarnings("varargs")
    public Federator(Chain<? extends Processor> ... chains) {
        this.chains = Arrays.asList(chains);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Response process(Request request, Execution execution) {
        List<FutureResponse> futureResponses=new ArrayList<>(chains.size());
        for (Chain<? extends Processor> chain : chains) {
            futureResponses.add(new AsyncExecution(chain,execution).process(request));
        }
        Response response=execution.process(request);
        AsyncExecution.waitForAll(futureResponses,1000);
        for (FutureResponse futureResponse : futureResponses) {
            Response federatedResponse=futureResponse.get();
            response.data().add(federatedResponse.data());
            response.mergeWith(federatedResponse);
        }
        return response;
    }
}
