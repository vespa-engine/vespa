// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.test;

import com.yahoo.jdisc.Request;
import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.application.Application;
import com.yahoo.jdisc.application.ContainerBuilder;
import com.yahoo.jdisc.handler.AbstractRequestHandler;
import com.yahoo.jdisc.handler.CompletionHandler;
import com.yahoo.jdisc.handler.ContentChannel;
import com.yahoo.jdisc.handler.RequestDeniedException;
import com.yahoo.jdisc.handler.ResponseHandler;
import com.yahoo.jdisc.service.ContainerNotReadyException;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;


/**
 * @author Simon Thoresen Hult
 */
public class TestDriverTestCase {

    @Test
    void requireThatFactoryMethodsWork() {
        TestDriver.newInjectedApplicationInstance(MyApplication.class).close();
        TestDriver.newInjectedApplicationInstanceWithoutOsgi(MyApplication.class).close();
        TestDriver.newInjectedApplicationInstance(new MyApplication()).close();
        TestDriver.newInjectedApplicationInstanceWithoutOsgi(new MyApplication()).close();
        TestDriver.newSimpleApplicationInstance().close();
        TestDriver.newSimpleApplicationInstanceWithoutOsgi().close();
    }

    @Test
    void requireThatAccessorsWork() {
        TestDriver driver = TestDriver.newSimpleApplicationInstanceWithoutOsgi();
        assertNotNull(driver.bootstrapLoader());
        assertTrue(driver.close());
    }

    @Test
    void requireThatConnectRequestWorks() {
        TestDriver driver = TestDriver.newSimpleApplicationInstanceWithoutOsgi();
        MyRequestHandler requestHandler = new MyRequestHandler(new MyContentChannel());
        ContainerBuilder builder = driver.newContainerBuilder();
        builder.serverBindings().bind("scheme://host/path", requestHandler);
        driver.activateContainer(builder);
        ContentChannel content = driver.connectRequest("scheme://host/path", new MyResponseHandler());
        assertNotNull(content);
        content.close(null);
        assertNotNull(requestHandler.handler);
        requestHandler.handler.handleResponse(new Response(Response.Status.OK)).close(null);
        assertTrue(driver.close());
    }

    @Test
    void requireThatDispatchRequestWorks() {
        TestDriver driver = TestDriver.newSimpleApplicationInstanceWithoutOsgi();
        MyRequestHandler requestHandler = new MyRequestHandler(new MyContentChannel());
        ContainerBuilder builder = driver.newContainerBuilder();
        builder.serverBindings().bind("scheme://host/path", requestHandler);
        driver.activateContainer(builder);
        driver.dispatchRequest("scheme://host/path", new MyResponseHandler());
        assertNotNull(requestHandler.handler);
        assertTrue(requestHandler.content.closed);
        requestHandler.handler.handleResponse(new Response(Response.Status.OK)).close(null);
        assertTrue(driver.close());
    }

    @Test
    void requireThatFailedRequestCreateDoesNotBlockClose() {
        TestDriver driver = TestDriver.newSimpleApplicationInstanceWithoutOsgi();
        try {
            driver.connectRequest("scheme://host/path", new MyResponseHandler());
            fail();
        } catch (ContainerNotReadyException e) {

        }
        assertTrue(driver.close());
    }

    @Test
    void requireThatFailedRequestConnectDoesNotBlockClose() {
        TestDriver driver = TestDriver.newSimpleApplicationInstanceWithoutOsgi();
        ContainerBuilder builder = driver.newContainerBuilder();
        builder.serverBindings().bind("scheme://host/path", new MyRequestHandler(null));
        driver.activateContainer(builder);
        try {
            driver.connectRequest("scheme://host/path", new MyResponseHandler());
            fail();
        } catch (RequestDeniedException e) {

        }
        assertTrue(driver.close());
    }

    private static class MyApplication implements Application {

        @Override
        public void start() {

        }

        @Override
        public void stop() {

        }

        @Override
        public void destroy() {

        }
    }

    private static class MyRequestHandler extends AbstractRequestHandler {

        final MyContentChannel content;
        ResponseHandler handler;

        MyRequestHandler(MyContentChannel content) {
            this.content = content;
        }

        @Override
        public ContentChannel handleRequest(Request request, ResponseHandler handler) {
            this.handler = handler;
            if (content == null) {
                throw new RequestDeniedException(request);
            }
            return content;
        }
    }

    private static class MyResponseHandler implements ResponseHandler {

        final MyContentChannel content = new MyContentChannel();

        @Override
        public ContentChannel handleResponse(Response response) {
            return content;
        }
    }

    private static class MyContentChannel implements ContentChannel {

        boolean closed = false;

        @Override
        public void write(ByteBuffer buf, CompletionHandler handler) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void close(CompletionHandler handler) {
            closed = true;
            handler.completed();
        }
    }
}
