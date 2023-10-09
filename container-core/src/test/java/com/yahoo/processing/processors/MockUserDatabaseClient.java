// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.processing.processors;

import com.yahoo.component.chain.dependencies.Provides;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.jdisc.handler.*;
import com.yahoo.processing.Processor;
import com.yahoo.processing.Request;
import com.yahoo.processing.Response;
import com.yahoo.processing.execution.Execution;

import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Provides("User")
public class MockUserDatabaseClient extends Processor {

    @Override
    public Response process(Request request, Execution execution) {
        try {
            Dispatch.CompleteResponse response =
                    new Dispatch("pio://endpoint/parameters",request).get(request.properties().getInteger("timeout"), TimeUnit.MILLISECONDS);
            User user = decodeResponseToUser(response);
            request.properties().set("User", user);
            return execution.process(request);
        }
        catch (InterruptedException | ExecutionException | TimeoutException e) {
            throw new RuntimeException("Exception waiting for database", e);
        }
    }

    private User decodeResponseToUser(Dispatch.CompleteResponse response) {
        // Just a mock implementation ...
        String responseData = response.nextString();
        if ( ! responseData.startsWith("id="))
            throw new IllegalArgumentException("Unexpected response "  + responseData);
        int newLine = responseData.indexOf("\n");
        if (newLine<0)
            throw new IllegalArgumentException("Unexpected response "  + responseData);
        String id = responseData.substring(3,newLine);

        // Make sure to always consume all
        while ( (responseData=response.nextString()) !=null) { }

        return new User(id);
    }

    // TODO: Move this to a top-level class
    public static class User {

        // TODO: OO model of users

        private String id;

        public User(String id) {
            this.id = id;
        }

        public String getId() { return id; }

    }

    private static class Dispatch {

        private final SimpleRequestDispatch requestDispatch;

        public Dispatch(String requestUri,Request request) {
            this.requestDispatch = new SimpleRequestDispatch(requestUri, request);
        }

        public CompleteResponse get(int timeout, TimeUnit timeUnit) throws InterruptedException, ExecutionException, TimeoutException {
            return new CompleteResponse(requestDispatch.get(timeout, timeUnit),
                                        requestDispatch.getResponseContent());
        }

        public static class CompleteResponse {

            private final com.yahoo.jdisc.Response response;
            private final ReadableContentChannel responseData;

            public CompleteResponse(com.yahoo.jdisc.Response response, ReadableContentChannel responseData) {
                this.response = response;
                this.responseData = responseData;
            }

            public com.yahoo.jdisc.Response response() { return response; }

            public ReadableContentChannel responseData() { return responseData; }

            /**
             * Convenience which returns the next piece of content from the response data of this as a string, or
             * null if there is no more data. The channel must always be consumed until there is no more data.
             */
            private String nextString() {
                ByteBuffer nextBuffer = responseData.read();
                if (nextBuffer == null) return null; // end of transmission
                return Charset.forName("utf-8").decode(nextBuffer).toString();
            }

        }

        private static class SimpleRequestDispatch extends RequestDispatch {

            private final URI requestUri;
            private final com.yahoo.jdisc.Request parentRequest;
            private final ReadableContentChannel responseData = new ReadableContentChannel();

            public SimpleRequestDispatch(String requestUri,Request request) {
                this.requestUri = URI.create(requestUri);
                this.parentRequest = ((HttpRequest)request.properties().get("jdisc.request")).getJDiscRequest();
                dispatch();
            }

            @Override
            protected com.yahoo.jdisc.Request newRequest() {
                return new com.yahoo.jdisc.Request(parentRequest, requestUri);
            }

            @Override
            public ContentChannel handleResponse(com.yahoo.jdisc.Response response) {
                return responseData;
            }

            public ReadableContentChannel getResponseContent() {
                return responseData;
            }

        }

    }

}
