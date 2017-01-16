// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ${package};

import com.yahoo.processing.*;
import com.yahoo.processing.execution.Execution;

public class ExampleProcessor extends Processor {

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