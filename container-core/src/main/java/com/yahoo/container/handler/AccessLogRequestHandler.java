// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.handler;

import com.yahoo.component.annotation.Inject;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.yahoo.jdisc.Metric;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.jdisc.ThreadedHttpRequestHandler;
import com.yahoo.container.logging.CircularArrayAccessLogKeeper;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * Exposes access log through http.
 *
 * @author dybis
 */
public class AccessLogRequestHandler extends ThreadedHttpRequestHandler {

    private final CircularArrayAccessLogKeeper circularArrayAccessLogKeeper;
    private final JsonFactory jsonFactory = new JsonFactory();

    @Inject
    public AccessLogRequestHandler(Executor executor, Metric metric, CircularArrayAccessLogKeeper circularArrayAccessLogKeeper) {
        super(executor, metric);
        this.circularArrayAccessLogKeeper = circularArrayAccessLogKeeper;
    }

    @Override
    public HttpResponse handle(HttpRequest request) {
        final List<String> uris = circularArrayAccessLogKeeper.getUris();

        return new HttpResponse(200) {
            @Override
            public void render(OutputStream outputStream) throws IOException {

                JsonGenerator generator = jsonFactory.createGenerator(outputStream);
                generator.writeStartObject();
                generator.writeArrayFieldStart("entries");
                for (String uri : uris) {
                    generator.writeStartObject();
                    generator.writeStringField("url", uri);
                    generator.writeEndObject();
                }
                generator.writeEndArray();
                generator.writeEndObject();
                generator.close();
            }
        };
    }

}
