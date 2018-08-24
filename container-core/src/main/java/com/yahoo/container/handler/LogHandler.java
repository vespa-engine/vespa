package com.yahoo.container.handler;

import com.fasterxml.jackson.core.JsonFactory;
import com.google.inject.Inject;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.jdisc.ThreadedHttpRequestHandler;
import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.Executor;

public class LogHandler extends ThreadedHttpRequestHandler {

    private static final String LOG_DIRECTORY = "/home/y/logs/vespa/";

    @Inject
    public LogHandler(Executor executor) {
        super(executor);
    }

    @Override
    public HttpResponse handle(HttpRequest request) {

        return new HttpResponse(200) {
            @Override
            public void render(OutputStream outputStream) throws IOException {
                LogReader.writeToOutputStream(LOG_DIRECTORY, outputStream);
            }
        };
    }
}
