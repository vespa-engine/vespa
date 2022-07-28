// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.benchmark;

import com.yahoo.jdisc.Request;
import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.application.ContainerBuilder;
import com.yahoo.jdisc.handler.AbstractRequestHandler;
import com.yahoo.jdisc.handler.CompletionHandler;
import com.yahoo.jdisc.handler.ContentChannel;
import com.yahoo.jdisc.handler.ResponseHandler;
import com.yahoo.jdisc.service.CurrentContainer;
import com.yahoo.jdisc.test.TestDriver;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.ByteBuffer;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Simon Thoresen Hult
 */
public class LatencyTestCase {

    private static final int NUM_REQUESTS = 100;

    @Test
    void runLatencyMeasurements() {
        TestDriver driver = TestDriver.newSimpleApplicationInstanceWithoutOsgi();
        ContainerBuilder builder = driver.newContainerBuilder();
        MyRequestHandler foo = new MyRequestHandler("foo");
        MyRequestHandler bar = new MyRequestHandler("bar");
        MyRequestHandler baz = new MyRequestHandler("baz");
        builder.serverBindings().bind(foo.uri, foo);
        builder.serverBindings().bind(bar.uri, bar);
        builder.serverBindings().bind(baz.uri, baz);
        driver.activateContainer(builder);

        measureLatencies(NUM_REQUESTS, driver, foo, bar, baz);
        TimeTrack time = measureLatencies(NUM_REQUESTS, driver, foo, bar, baz);
        System.err.println("\n" + time);

        foo.release();
        bar.release();
        baz.release();
        assertTrue(driver.close());
    }

    private static TimeTrack measureLatencies(int numRequests, CurrentContainer container,
                                              MyRequestHandler... requestHandlers)
    {
        TimeTrack track = new TimeTrack();
        Random rnd = new Random();
        for (int i = 0; i < numRequests; ++i) {
            track.add(measureLatency(container, requestHandlers[rnd.nextInt(requestHandlers.length)]));
        }
        return track;
    }

    private static TimeFrame measureLatency(CurrentContainer container, MyRequestHandler requestHandler) {
        TimeFrame frame = new TimeFrame();

        Request request = null;
        ContentChannel requestContent = null;
        MyResponseHandler responseHandler = new MyResponseHandler();
        try {
            URI uri = URI.create(requestHandler.uri);
            request = new Request(container, uri);
            frame.handleRequestBegin = System.nanoTime();
            requestContent = request.connect(responseHandler);
            frame.handleRequestEnd = requestHandler.handleTime;
        } finally {
            if (request != null) {
                request.release();
            }
        }
        ByteBuffer buf = ByteBuffer.allocate(69);
        MyCompletion requestWrite = new MyCompletion();
        frame.requestWriteBegin = System.nanoTime();
        requestContent.write(buf, requestWrite);
        frame.requestWriteEnd = requestHandler.requestContent.writeTime;
        frame.requestWriteCompletionBegin = System.nanoTime();
        requestHandler.requestContent.writeCompletion.completed();
        frame.requestWriteCompletionEnd = requestWrite.completedTime;

        MyCompletion requestClose = new MyCompletion();
        frame.requestCloseBegin = System.nanoTime();
        requestContent.close(requestClose);
        frame.requestCloseEnd = requestHandler.requestContent.closeTime;
        frame.requestCloseCompletionBegin = System.nanoTime();
        requestHandler.requestContent.closeCompletion.completed();
        frame.requestCloseCompletionEnd = requestClose.completedTime;

        Response response = new Response(Response.Status.OK);
        frame.handleResponseBegin = System.nanoTime();
        ContentChannel responseContent = requestHandler.responseHandler.handleResponse(response);
        frame.handleResponseEnd = responseHandler.handleTime;
        MyCompletion responseWrite = new MyCompletion();
        frame.responseWriteBegin = System.nanoTime();
        responseContent.write(buf, responseWrite);
        frame.responseWriteEnd = responseHandler.responseContent.writeTime;
        frame.responseWriteCompletionBegin = System.nanoTime();
        responseHandler.responseContent.writeCompletion.completed();
        frame.responseWriteCompletionEnd = responseWrite.completedTime;

        MyCompletion responseClose = new MyCompletion();
        frame.responseCloseBegin = System.nanoTime();
        responseContent.close(responseClose);
        frame.responseCloseEnd = responseHandler.responseContent.closeTime;
        frame.responseCloseCompletionBegin = System.nanoTime();
        responseHandler.responseContent.closeCompletion.completed();
        frame.responseCloseCompletionEnd = responseClose.completedTime;

        return frame;
    }

    private static class MyRequestHandler extends AbstractRequestHandler {

        final MyContent requestContent = new MyContent();
        final String uri;
        long handleTime;
        Request request;
        ResponseHandler responseHandler;

        MyRequestHandler(String path) {
            this.uri = "http://localhost/" + path;
        }

        @Override
        public ContentChannel handleRequest(Request request, ResponseHandler handler) {
            handleTime = System.nanoTime();
            this.request = request;
            responseHandler = handler;
            return requestContent;
        }
    }

    private static class MyResponseHandler implements ResponseHandler {

        final MyContent responseContent = new MyContent();
        long handleTime;

        @Override
        public ContentChannel handleResponse(Response response) {
            handleTime = System.nanoTime();
            return responseContent;
        }
    }

    private static class MyContent implements ContentChannel {

        long writeTime;
        long closeTime;
        CompletionHandler writeCompletion;
        CompletionHandler closeCompletion;

        @Override
        public void write(ByteBuffer buf, CompletionHandler handler) {
            writeTime = System.nanoTime();
            writeCompletion = handler;
        }

        @Override
        public void close(CompletionHandler handler) {
            closeTime = System.nanoTime();
            closeCompletion = handler;
        }
    }

    private static class MyCompletion implements CompletionHandler {

        long completedTime;

        @Override
        public void completed() {
            completedTime = System.nanoTime();
        }

        @Override
        public void failed(Throwable t) {

        }
    }

    private static class TimeFrame {

        long handleRequestBegin;
        long handleRequestEnd;
        long requestWriteBegin;
        long requestWriteEnd;
        long requestWriteCompletionBegin;
        long requestWriteCompletionEnd;
        long requestCloseBegin;
        long requestCloseEnd;
        long requestCloseCompletionBegin;
        long requestCloseCompletionEnd;
        long handleResponseBegin;
        long handleResponseEnd;
        long responseWriteBegin;
        long responseWriteEnd;
        long responseWriteCompletionBegin;
        long responseWriteCompletionEnd;
        long responseCloseBegin;
        long responseCloseEnd;
        long responseCloseCompletionBegin;
        long responseCloseCompletionEnd;
    }

    private static class TimeTrack {

        long frameCnt = 0;
        long handleRequest;
        long requestWrite;
        long requestWriteCompletion;
        long requestClose;
        long requestCloseCompletion;
        long handleResponse;
        long responseWrite;
        long responseWriteCompletion;
        long responseClose;
        long responseCloseCompletion;

        public void add(TimeFrame frame) {
            ++frameCnt;
            handleRequest += frame.handleRequestEnd - frame.handleRequestBegin;
            requestWrite += frame.requestWriteEnd - frame.requestWriteBegin;
            requestWriteCompletion += frame.requestWriteCompletionEnd - frame.requestWriteCompletionBegin;
            requestClose += frame.requestCloseEnd - frame.requestCloseBegin;
            requestCloseCompletion += frame.requestCloseCompletionEnd - frame.requestCloseCompletionBegin;
            handleResponse += frame.handleResponseEnd - frame.handleResponseBegin;
            responseWrite += frame.responseWriteEnd - frame.responseWriteBegin;
            responseWriteCompletion += frame.responseWriteCompletionEnd - frame.responseWriteCompletionBegin;
            responseClose += frame.responseCloseEnd - frame.responseCloseBegin;
            responseCloseCompletion += frame.responseCloseCompletionEnd - frame.responseCloseCompletionBegin;
        }

        @Override
        public String toString() {
            StringBuilder ret = new StringBuilder();
            ret.append("------------------------------------\n");
            ret.append(String.format("HandleRequest           : %10.2f\n", (double)handleRequest / frameCnt));
            ret.append(String.format("RequestWrite            : %10.2f\n", (double)requestWrite / frameCnt));
            ret.append(String.format("RequestWriteCompletion  : %10.2f\n", (double)requestWriteCompletion / frameCnt));
            ret.append(String.format("RequestClose            : %10.2f\n", (double)requestClose / frameCnt));
            ret.append(String.format("RequestCloseCompletion  : %10.2f\n", (double)requestCloseCompletion / frameCnt));
            ret.append(String.format("HandleResponse          : %10.2f\n", (double)handleResponse / frameCnt));
            ret.append(String.format("ResponseWrite           : %10.2f\n", (double)responseWrite / frameCnt));
            ret.append(String.format("ResponseWriteCompletion : %10.2f\n", (double)responseWriteCompletion / frameCnt));
            ret.append(String.format("ResponseClose           : %10.2f\n", (double)responseClose / frameCnt));
            ret.append(String.format("ResponseCloseCompletion : %10.2f\n", (double)responseCloseCompletion / frameCnt));
            ret.append("------------------------------------\n");

            double time = (handleRequest + requestWrite + requestWriteCompletion + requestClose +
                           requestCloseCompletion + handleResponse + responseWrite + responseWriteCompletion +
                           responseClose + responseCloseCompletion) / frameCnt;
            ret.append(String.format("Total nanos             : %10.2f\n", time));
            ret.append(String.format("Requests per second     : %10.2f\n", TimeUnit.SECONDS.toNanos(1) / time));
            ret.append("------------------------------------\n");
            return ret.toString();
        }
    }
}
