// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.application.container;

import com.google.common.annotations.Beta;
import com.yahoo.application.container.handler.Request;
import com.yahoo.application.container.handler.Response;
import com.yahoo.jdisc.handler.CompletionHandler;
import com.yahoo.jdisc.handler.ContentChannel;
import com.yahoo.jdisc.handler.RequestDispatch;
import com.yahoo.jdisc.handler.ResponseHandler;
import com.yahoo.jdisc.service.CurrentContainer;
import com.yahoo.jdisc.test.TestDriver;

import javax.annotation.concurrent.ThreadSafe;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

/**
 * @author Einar M R Rosenvinge
 */
@ThreadSafe
@Beta
final class SynchronousRequestResponseHandler {

    Response handleRequest(Request request, TestDriver driver)  {
        BlockingResponseHandler responseHandler = new BlockingResponseHandler();
        ContentChannel inputRequestChannel =  connectRequest(request, driver, responseHandler);
        writeRequestBody(request, inputRequestChannel);
        return responseHandler.getResponse();
    }

    private void writeRequestBody(Request request, ContentChannel inputRequestChannel) {
        List<BlockingCompletionHandler> completionHandlers = new ArrayList<>();

        if (request.getBody().length > 0) {
            BlockingCompletionHandler w = new BlockingCompletionHandler();
            try {
                inputRequestChannel.write(ByteBuffer.wrap(request.getBody()), w);
                completionHandlers.add(w);
            } finally {
                BlockingCompletionHandler c = new BlockingCompletionHandler();
                inputRequestChannel.close(c);
                completionHandlers.add(c);
            }
        } else {
            BlockingCompletionHandler c = new BlockingCompletionHandler();
            inputRequestChannel.close(c);
            completionHandlers.add(c);
        }

        for (BlockingCompletionHandler completionHandler : completionHandlers) {
            completionHandler.waitUntilCompleted();
        }
    }

    private ContentChannel connectRequest(final Request request,
                                          final TestDriver driver,
                                          final ResponseHandler responseHandler) {
        RequestDispatch dispatch =
                new RequestDispatch() {
                    @Override
                    protected com.yahoo.jdisc.Request newRequest() {
                        return createDiscRequest(request, driver);
                    }

                    @Override
                    public ContentChannel handleResponse(com.yahoo.jdisc.Response response) {
                        return responseHandler.handleResponse(response);
                    }
                };
        return dispatch.connect();
    }

    private static String getScheme(String uri) {
        int colonPos = uri.indexOf(':');
        if (colonPos < 0) {
            return "";
        }
        return uri.substring(0, colonPos);
    }


    private static com.yahoo.jdisc.Request createDiscRequest(Request request, CurrentContainer currentContainer) {
        String scheme = getScheme(request.getUri());
        com.yahoo.jdisc.Request discRequest;
        if ("http".equals(scheme) || "https".equals(scheme)) {
            com.yahoo.jdisc.http.HttpRequest httpRequest = com.yahoo.jdisc.http.HttpRequest.newServerRequest(currentContainer,
                                                                            URI.create(request.getUri()),
                                                                            com.yahoo.jdisc.http.HttpRequest.Method.valueOf(request.getMethod().name()));
            request.getUserPrincipal().ifPresent(httpRequest::setUserPrincipal);
            discRequest = httpRequest;
        } else {
            discRequest = new com.yahoo.jdisc.Request(currentContainer, URI.create(request.getUri()));
        }
        for (Map.Entry<String, List<String>> entry : request.getHeaders().entrySet()) {
            discRequest.headers().add(entry.getKey(), entry.getValue());
        }
        discRequest.context().putAll(request.getAttributes());
        return discRequest;
    }

    private static byte[] concatenateBuffers(List<ByteBuffer> byteBuffers) {
        int totalSize = 0;
        for (ByteBuffer responseBuffer : byteBuffers) {
            totalSize += responseBuffer.remaining();
        }
        ByteBuffer totalBuffer = ByteBuffer.allocate(totalSize);
        for (ByteBuffer responseBuffer : byteBuffers) {
            totalBuffer.put(responseBuffer);
        }
        return totalBuffer.array();
    }

    private static void copyResponseHeaders(Response response, com.yahoo.jdisc.Response discResponse) {
        for (Map.Entry<String, List<String>> entry : discResponse.headers().entrySet()) {
            response.getHeaders().put(entry.getKey(), entry.getValue());
        }
    }

    @ThreadSafe
    private static class BlockingResponseHandler implements ResponseHandler, ContentChannel {
        private volatile com.yahoo.jdisc.Response discResponse = null;
        private CountDownLatch closedLatch = new CountDownLatch(1);
        private final List<ByteBuffer> responseBuffers = new ArrayList<>();

        @Override
        public ContentChannel handleResponse(com.yahoo.jdisc.Response discResponse) {
            this.discResponse = discResponse;
            return this;
        }

        public Response getResponse() {
            try {
                closedLatch.await();
            } catch (InterruptedException e) {
                throw new ApplicationException(e);
            }
            byte[] totalBuffer = concatenateBuffers(responseBuffers);
            Response response = new Response(discResponse.getStatus(), totalBuffer);
            copyResponseHeaders(response, discResponse);
            return response;
        }

        @Override
        public void write(ByteBuffer byteBuffer, CompletionHandler completionHandler) {
            responseBuffers.add(byteBuffer);
            completionHandler.completed();
        }

        @Override
        public void close(CompletionHandler completionHandler) {
            completionHandler.completed();
            closedLatch.countDown();
        }
    }

    private static class BlockingCompletionHandler implements CompletionHandler {

        private volatile Throwable throwable;
        private CountDownLatch doneLatch = new CountDownLatch(1);

        @Override
        public void completed() {
            doneLatch.countDown();
        }

        @Override
        public void failed(Throwable t) {
            throwable = t;
            doneLatch.countDown();
        }

        public void waitUntilCompleted() {
            try {
                doneLatch.await();
            } catch (InterruptedException e) {
                throw new ApplicationException(e);
            }
            if (throwable != null) {
                if (throwable instanceof RuntimeException) {
                    throw (RuntimeException) throwable;
                } else {
                    throw new RuntimeException(throwable);
                }
            }
        }

    }

}
