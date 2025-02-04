// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.restapi.resource;

import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.jdisc.ThreadedHttpRequestHandler;
import com.yahoo.jdisc.Metric;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.JsonFormat;
import com.yahoo.slime.Slime;

import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.Executor;

/**
 * HTTP handler for a mock /document/v1 API, used when &lt;document-api&gt; is not specified
 * in services.xml. Returns http status code 404 and a message describing that API is not configured,
 * pointing to documentation for how to do that.
 *
 * @author hmusum
 */
@SuppressWarnings("unused") // Created by config model
public final class DummyDocumentV1ApiHandler extends ThreadedHttpRequestHandler {

    @SuppressWarnings("unused") // Created by config model
    public DummyDocumentV1ApiHandler(Executor executor, Metric metrics) {
        super(executor, metrics);
    }

    @Override
    public HttpResponse handle(HttpRequest request) {
        return new HttpErrorResponse(404, "Document API is not configured in this cluster. See https://docs.vespa.ai/en/reference/services-container.html#document-api");
    }

    private static class HttpErrorResponse extends HttpResponse {

        private final Slime slime = new Slime();

        public HttpErrorResponse(int code, String msg) {
            super(code);
            Cursor root = slime.setObject();
            root.setString("message", msg);
        }


        @Override
        public void render(OutputStream stream) throws IOException {
            new JsonFormat(true).encode(stream, slime);
        }

        @Override
        public String getContentType() { return "application/json"; }

    }

}
