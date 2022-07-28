// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.service;

import com.yahoo.jdisc.Request;
import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.application.ContainerBuilder;
import com.yahoo.jdisc.handler.AbstractRequestHandler;
import com.yahoo.jdisc.handler.CompletionHandler;
import com.yahoo.jdisc.handler.ContentChannel;
import com.yahoo.jdisc.handler.ResponseHandler;
import com.yahoo.jdisc.test.TestDriver;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.fail;


/**
 * @author Simon Thoresen Hult
 */
public class ConnectToHandlerTestCase {

    @Test
    void requireThatNullResponseHandlerThrowsException() {
        TestDriver driver = TestDriver.newSimpleApplicationInstanceWithoutOsgi();
        driver.activateContainer(driver.newContainerBuilder());
        Request request = new Request(driver, URI.create("http://host/path"));
        try {
            request.connect(null);
            fail();
        } catch (NullPointerException e) {
            // expected
        }
        request.release();
        driver.close();
    }

    @Test
    void requireThatConnectToHandlerWorks() {
        TestDriver driver = TestDriver.newSimpleApplicationInstanceWithoutOsgi();
        MyRequestHandler requestHandler = new MyRequestHandler(new MyContent());
        ContainerBuilder builder = driver.newContainerBuilder();
        builder.serverBindings().bind("http://host/*", requestHandler);
        driver.activateContainer(builder);
        Request request = new Request(driver, URI.create("http://host/path"));
        MyResponseHandler responseHandler = new MyResponseHandler();
        ContentChannel content = request.connect(responseHandler);
        request.release();
        assertNotNull(content);
        content.close(null);
        assertNotNull(requestHandler.handler);
        assertSame(request, requestHandler.request);
        requestHandler.handler.handleResponse(new Response(Response.Status.OK)).close(null);
        driver.close();
    }

    private class MyRequestHandler extends AbstractRequestHandler {

        final ContentChannel content;
        Request request;
        ResponseHandler handler;

        MyRequestHandler(ContentChannel content) {
            this.content = content;
        }

        @Override
        public ContentChannel handleRequest(Request request, ResponseHandler handler) {
            this.request = request;
            this.handler = handler;
            return content;
        }
    }

    private class MyResponseHandler implements ResponseHandler {

        @Override
        public ContentChannel handleResponse(Response response) {
            return null;
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
