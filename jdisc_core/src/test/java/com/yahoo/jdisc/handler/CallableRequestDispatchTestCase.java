// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.handler;

import com.yahoo.jdisc.Request;
import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.application.ContainerBuilder;
import com.yahoo.jdisc.test.TestDriver;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Simon Thoresen Hult
 */
public class CallableRequestDispatchTestCase {

    @Test
    void requireThatDispatchIsCalled() throws Exception {
        final TestDriver driver = TestDriver.newSimpleApplicationInstanceWithoutOsgi();
        ContainerBuilder builder = driver.newContainerBuilder();
        Response response = new Response(Response.Status.OK);
        builder.serverBindings().bind("http://host/path", new MyRequestHandler(response));
        driver.activateContainer(builder);
        assertSame(response, new CallableRequestDispatch() {

            @Override
            protected Request newRequest() {
                return new Request(driver, URI.create("http://host/path"));
            }
        }.call());
        assertTrue(driver.close());
    }

    private static class MyRequestHandler extends AbstractRequestHandler {

        final Response response;

        MyRequestHandler(Response response) {
            this.response = response;
        }

        @Override
        public ContentChannel handleRequest(Request request, ResponseHandler handler) {
            ResponseDispatch.newInstance(response).dispatch(handler);
            return null;
        }
    }
}
