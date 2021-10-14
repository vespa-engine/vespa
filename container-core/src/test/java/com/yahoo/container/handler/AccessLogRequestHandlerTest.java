// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.handler;

import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.logging.CircularArrayAccessLogKeeper;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.Executor;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;

public class AccessLogRequestHandlerTest {

    private final CircularArrayAccessLogKeeper keeper = new CircularArrayAccessLogKeeper();
    private final Executor executor = mock(Executor.class);
    private final AccessLogRequestHandler handler = new AccessLogRequestHandler(executor, null, keeper);
    private final ByteArrayOutputStream out = new ByteArrayOutputStream();

    @Test
    public void testOneLogLine() throws IOException {
        keeper.addUri("foo");
        HttpResponse response = handler.handle(null);
        response.render(out);
        assertThat(out.toString(), is("{\"entries\":[{\"url\":\"foo\"}]}"));
    }

    @Test
    public void testEmpty() throws IOException {
        HttpResponse response = handler.handle(null);
        response.render(out);
        assertThat(out.toString(), is("{\"entries\":[]}"));
    }

    @Test
    public void testManyLogLines() throws IOException {
        keeper.addUri("foo");
        keeper.addUri("foo");
        HttpResponse response = handler.handle(null);
        response.render(out);
        assertThat(out.toString(), is("{\"entries\":[{\"url\":\"foo\"},{\"url\":\"foo\"}]}"));
    }

}
