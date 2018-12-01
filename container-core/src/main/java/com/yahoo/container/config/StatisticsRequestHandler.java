// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.config;

import com.google.inject.Inject;
import com.yahoo.jdisc.Metric;
import com.yahoo.container.ConfigHack;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.jdisc.ThreadedHttpRequestHandler;
import com.yahoo.container.logging.AccessLog;
import com.yahoo.text.Utf8;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.concurrent.Executor;

/**
 * Handler of statistics http requests. Temporary hack as a step towards a more
 * general network interface.
 *
 * @author Steinar Knutsen
 * @author Einar M R Rosenvinge
 */
public class StatisticsRequestHandler extends ThreadedHttpRequestHandler {

    @Inject
    public StatisticsRequestHandler(Executor executor, Metric metric) {
        super(executor, metric);
    }

    @Override
    public HttpResponse handle(HttpRequest request) {
        return new StatisticsResponse(ConfigHack.instance.getStatisticsHandler().respond(request));
    }

    protected static class StatisticsResponse extends HttpResponse {

        private final StringBuilder string;

        private StatisticsResponse(StringBuilder stringBuilder) {
            super(com.yahoo.jdisc.http.HttpResponse.Status.OK);
            this.string = stringBuilder;
        }

        @Override
        public void render(OutputStream stream) throws IOException {
            Writer osWriter = new OutputStreamWriter(stream, Utf8.getCharset());
            osWriter.write(string.toString());
            osWriter.flush();
            osWriter.close();
        }
    }
}
