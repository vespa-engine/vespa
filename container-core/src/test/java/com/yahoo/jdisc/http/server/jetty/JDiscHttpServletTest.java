// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

import com.yahoo.jdisc.Request;
import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.handler.AbstractRequestHandler;
import com.yahoo.jdisc.handler.ContentChannel;
import com.yahoo.jdisc.handler.RequestHandler;
import com.yahoo.jdisc.handler.ResponseHandler;
import org.apache.hc.client5.http.classic.methods.HttpDelete;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpHead;
import org.apache.hc.client5.http.classic.methods.HttpOptions;
import org.apache.hc.client5.http.classic.methods.HttpPatch;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.classic.methods.HttpPut;
import org.apache.hc.client5.http.classic.methods.HttpTrace;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.junit.jupiter.api.Test;

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
    void requireThatServerRespondsToAllMethods() throws Exception {
        final JettyTestDriver driver = JettyTestDriver.newInstance(newEchoHandler());
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
    void requireThatServerResponds405ToUnknownMethods() throws IOException {
        JettyTestDriver driver = JettyTestDriver.newInstance(newEchoHandler());
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

    private static class UnknownMethodHttpRequest extends HttpUriRequestBase {
        UnknownMethodHttpRequest(URI uri) { super("UNKNOWN_METHOD", uri); }
    }
}
