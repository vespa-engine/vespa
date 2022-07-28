// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.core;

import com.yahoo.jdisc.Request;
import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.application.ContainerBuilder;
import com.yahoo.jdisc.handler.AbstractRequestHandler;
import com.yahoo.jdisc.handler.CompletionHandler;
import com.yahoo.jdisc.handler.ContentChannel;
import com.yahoo.jdisc.handler.RequestHandler;
import com.yahoo.jdisc.handler.ResponseHandler;
import com.yahoo.jdisc.test.TestDriver;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Simon Thoresen Hult
 */
public class ApplicationShutdownTestCase {

    @Test
    void requireThatStopWaitsForPreviousContainer() throws Exception {
        Context ctx = new Context();
        MyRequestHandler requestHandler = new MyRequestHandler();
        ctx.activateContainer(requestHandler);
        ctx.dispatchRequest();
        ctx.activateContainer(null);
        ctx.driver.scheduleClose();
        assertFalse(ctx.driver.awaitClose(100, TimeUnit.MILLISECONDS));
        requestHandler.respond();
        assertTrue(ctx.driver.awaitClose(600, TimeUnit.SECONDS));
    }

    @Test
    void requireThatStopWaitsForAllPreviousContainers() {
        Context ctx = new Context();
        MyRequestHandler requestHandlerA = new MyRequestHandler();
        ctx.activateContainer(requestHandlerA);
        ctx.dispatchRequest();

        MyRequestHandler requestHandlerB = new MyRequestHandler();
        ctx.activateContainer(requestHandlerB);
        ctx.dispatchRequest();

        MyRequestHandler requestHandlerC = new MyRequestHandler();
        ctx.activateContainer(requestHandlerC);
        ctx.dispatchRequest();

        ctx.driver.scheduleClose();
        assertFalse(ctx.driver.awaitClose(100, TimeUnit.MILLISECONDS));
        requestHandlerB.respond();
        assertFalse(ctx.driver.awaitClose(100, TimeUnit.MILLISECONDS));
        requestHandlerC.respond();
        assertFalse(ctx.driver.awaitClose(100, TimeUnit.MILLISECONDS));
        requestHandlerA.respond();
        assertTrue(ctx.driver.awaitClose(600, TimeUnit.SECONDS));
    }

    private static class Context {

        final TestDriver driver = TestDriver.newSimpleApplicationInstanceWithoutOsgi();

        void activateContainer(RequestHandler requestHandler) {
            ContainerBuilder builder;
            if (requestHandler != null) {
                builder = driver.newContainerBuilder();
                builder.serverBindings().bind("http://host/path", requestHandler);
            } else {
                builder = null;
            }
            driver.activateContainer(builder);
        }

        void dispatchRequest() {
            Request request = new Request(driver, URI.create("http://host/path"));
            request.connect(new MyResponseHandler()).close(null);
            request.release();
        }
    }

    private static class MyRequestHandler extends AbstractRequestHandler {

        ResponseHandler handler = null;

        @Override
        public ContentChannel handleRequest(Request request, ResponseHandler handler) {
            this.handler = handler;
            return new MyContent();
        }

        void respond() {
            handler.handleResponse(new Response(Response.Status.OK)).close(null);
        }
    }

    private static class MyResponseHandler implements ResponseHandler {

        @Override
        public ContentChannel handleResponse(Response response) {
            return new MyContent();
        }
    }

    private static class MyContent implements ContentChannel {

        @Override
        public void write(ByteBuffer buf, CompletionHandler handler) {
            handler.completed();
        }

        @Override
        public void close(CompletionHandler handler) {
            handler.completed();
        }
    }
}
