// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.processing.processors;

import com.yahoo.processing.Processor;
import com.yahoo.processing.Request;
import com.yahoo.processing.Response;
import com.yahoo.processing.execution.Execution;

import java.util.Map;

/**
 * A processor which adds the current content of the Request.properties() to
 * the trace before calling the next processor, if traceLevel is 4 or more.
 *
 * @author bratseth
 */
public class RequestPropertyTracer extends Processor {

    @Override
    public Response process(Request request, Execution execution) {
        if (execution.trace().getTraceLevel()<4) return execution.process(request);

        StringBuilder b = new StringBuilder("{");
        for (Map.Entry<String,Object> property : request.properties().listProperties().entrySet())
            b.append(property.getKey()).append(": '").append(property.getValue()).append("',");
        b.setLength(b.length()-1); // remove last comma
        b.append("}");
        execution.trace().trace(b.toString(),4);
        return execution.process(request);
    }

}
