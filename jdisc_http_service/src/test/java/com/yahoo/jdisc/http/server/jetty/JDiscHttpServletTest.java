// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

import com.yahoo.jdisc.Request;
import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.handler.AbstractRequestHandler;
import com.yahoo.jdisc.handler.ContentChannel;
import com.yahoo.jdisc.handler.RequestHandler;
import com.yahoo.jdisc.handler.ResponseHandler;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpOptions;
import org.apache.http.client.methods.HttpPatch;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.methods.HttpTrace;
import org.junit.Test;

import java.io.IOException;
import java.net.URI;

import static com.yahoo.jdisc.Response.Status.METHOD_NOT_ALLOWED;
import static com.yahoo.jdisc.Response.Status.OK;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * @author Simon Thoresen Hult
 */
public class JDiscHttpServletTest {

    @Test
    public void requireThatServerRespondsToAllMethods() throws Exception {
        final TestDriver driver = TestDrivers.newInstance(newEchoHandler());
        final URI uri = driver.client().newUri("/status.html");
        driver.client().execute(new HttpGet(uri))
              .expectStatusCode(is(OK));
        driver.client().execute(new HttpPost(uri))
              .expectStatusCode(is(OK));
        driver.client().execute(new HttpHead(uri))
              .expectStatusCode(is(OK));
        driver.client().execute(new HttpPut(uri))
              .expectStatusCode(is(OK));
        driver.client().execute(new HttpDelete(uri))
              .expectStatusCode(is(OK));
        driver.client().execute(new HttpOptions(uri))
              .expectStatusCode(is(OK));
        driver.client().execute(new HttpTrace(uri))
              .expectStatusCode(is(OK));
        driver.client().execute(new HttpPatch(uri))
                .expectStatusCode(is(OK));
        assertThat(driver.close(), is(true));
    }

    @Test
    public void requireThatServerResponds405ToUnknownMethods() throws IOException {
        TestDriver driver = TestDrivers.newInstance(newEchoHandler());
        final URI uri = driver.client().newUri("/status.html");
        driver.client().execute(new UnknownMethodHttpRequest(uri))
                .expectStatusCode(is(METHOD_NOT_ALLOWED));
        assertThat(driver.close(), is(true));
    }

    private static RequestHandler newEchoHandler() {
        return new AbstractRequestHandler() {

            @Override
            public ContentChannel handleRequest(final Request request, final ResponseHandler handler) {
                return handler.handleResponse(new Response(OK));
            }
        };
    }

    private static class UnknownMethodHttpRequest extends HttpRequestBase {
        UnknownMethodHttpRequest(URI uri) { setURI(uri); }
        @Override public String getMethod() { return "UNKNOWN_METHOD"; }
    }
}
