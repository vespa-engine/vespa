// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.processing.test.documentation;

import com.yahoo.processing.*;
import com.yahoo.processing.execution.Execution;
import com.yahoo.processing.test.ProcessorLibrary.StringData;

public class ExampleProcessor extends Processor {

    @SuppressWarnings("unchecked")
    @Override
    public Response process(Request request, Execution execution) {
        // Process the Request:
        request.properties().set("foo","bar");

        // Pass it down the chain to get a response
        Response response=execution.process(request);

        // process the response
        response.data().add(new StringData(request,"Hello, world!"));

        return response;
    }

}
