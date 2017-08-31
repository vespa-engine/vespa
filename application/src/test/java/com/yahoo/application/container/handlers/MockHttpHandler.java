// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.application.container.handlers;

import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.jdisc.ThreadedHttpRequestHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.concurrent.Executor;

/**
 * @author Christian Andersen
 */
public class MockHttpHandler extends ThreadedHttpRequestHandler {

    public MockHttpHandler(Executor executor) {
        super(executor);
    }

    @Override
    public HttpResponse handle(HttpRequest request) {
        return new HttpResponse(200) {
            @Override
            public void render(OutputStream outputStream) throws IOException {
                PrintStream out = new PrintStream(outputStream);
                out.print("OK");
                out.flush();
            }
        };
    }

}
