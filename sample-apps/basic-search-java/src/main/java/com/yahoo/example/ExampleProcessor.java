// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.example;

import com.google.inject.Inject;
import com.yahoo.processing.Processor;
import com.yahoo.processing.Request;
import com.yahoo.processing.Response;
import com.yahoo.processing.execution.Execution;

public class ExampleProcessor extends Processor {

    private final String message;

    @Inject
    public ExampleProcessor(ExampleProcessorConfig config) {
        this.message = config.message();
    }

    @SuppressWarnings("unchecked")
    @Override
    public Response process(Request request, Execution execution) {
        // process the request
        request.properties().set("foo", "bar");

        // pass it down the chain to get a response
        Response response = execution.process(request);

        // process the response
        response.data().add(new StringData(request, message));

        // return the response up the chain
        return response;
    }
}
