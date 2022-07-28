// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.handler;

import com.yahoo.jdisc.NoopSharedResource;
import com.yahoo.jdisc.Request;
import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.application.ContainerBuilder;
import com.yahoo.jdisc.test.TestDriver;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author Simon Thoresen Hult
 */
public class RequestDeniedTestCase {

    @Test
    void requireThatAccessorsWork() {
        TestDriver driver = TestDriver.newSimpleApplicationInstanceWithoutOsgi();
        driver.activateContainer(driver.newContainerBuilder());
        Request request = new Request(driver, URI.create("http://host/path"));
        RequestDeniedException e = new RequestDeniedException(request);
        assertSame(request, e.request());
        request.release();
        driver.close();
    }

    @Test
    void requireThatRequestDeniedIsThrown() {
        TestDriver driver = TestDriver.newSimpleApplicationInstanceWithoutOsgi();
        ContainerBuilder builder = driver.newContainerBuilder();
        RequestHandler requestHandler = new MyRequestHandler();
        builder.serverBindings().bind("http://host/path", requestHandler);
        driver.activateContainer(builder);
        Request request = new Request(driver, URI.create("http://host/path"));
        try {
            request.connect(new MyResponseHandler());
            fail();
        } catch (RequestDeniedException e) {
            assertSame(request, e.request());
        }
        request.release();
        driver.close();
    }

    private static class MyRequestHandler extends NoopSharedResource implements RequestHandler {

        @Override
        public ContentChannel handleRequest(Request request, ResponseHandler handler) {
            throw new RequestDeniedException(request);
        }

        @Override
        public void handleTimeout(Request request, ResponseHandler handler) {

        }
    }

    private static class MyResponseHandler implements ResponseHandler {

        @Override
        public ContentChannel handleResponse(Response response) {
            return null;
        }
    }
}
