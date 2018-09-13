package com.yahoo.container.handler;

import com.google.inject.Inject;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.jdisc.ThreadedHttpRequestHandler;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.concurrent.Executor;

public class LogHandler extends ThreadedHttpRequestHandler {

    private static final String LOG_DIRECTORY = "/home/y/logs/vespa/";

    @Inject
    public LogHandler(Executor executor) {
        super(executor);
    }

    @Override
    public HttpResponse handle(HttpRequest request) {
        JSONObject logJson;
        LogReader logReader;

        try {
            if (request.hasProperty("numberOfLogs")) {
                int numberOfLogs = (Integer.valueOf(request.getProperty("numberOfLogs")));
                logReader = new LogReader(numberOfLogs);
            } else {
                logReader = new LogReader();
            }
            logJson = logReader.readLogs(LOG_DIRECTORY);
        } catch (IOException | JSONException e) {
            return new HttpResponse(404) {
                @Override
                public void render(OutputStream outputStream) {}
            };
        }
        return new HttpResponse(200) {
            @Override
            public void render(OutputStream outputStream) throws IOException {
                OutputStreamWriter outputStreamWriter = new OutputStreamWriter(outputStream);
                outputStreamWriter.write(logJson.toString());
                outputStreamWriter.close();
            }
        };
    }
}
