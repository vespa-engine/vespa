// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.application.container.processors;

import com.yahoo.processing.Processor;
import com.yahoo.processing.Request;
import com.yahoo.processing.Response;
import com.yahoo.processing.execution.Execution;
import com.yahoo.processing.response.AbstractData;

import static com.yahoo.application.container.docprocs.Rot13DocumentProcessor.rot13;

/**
 * @author Einar M R Rosenvinge
 */
public class Rot13Processor extends Processor {
    public static class StringData extends AbstractData {

        private String string;

        public StringData(Request request, String string) {
            super(request);
            this.string = string;
        }

        public void setString(String string) {
            this.string = string;
        }

        @Override
        public String toString() {
            return string;
        }

    }

    @SuppressWarnings("unchecked")
    @Override
    public Response process(Request request, Execution execution) {
        Object fooObj = request.properties().get("title");

        Response response = new Response(request);
        if (fooObj != null) {
            response.data().add(new StringData(request, rot13(fooObj.toString())));
        }
        return response;
    }

}
