// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http;

import com.yahoo.container.jdisc.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.util.logging.Level;
import java.util.logging.Logger;


public class LogRetriever {

    private final static Logger log = Logger.getLogger(LogRetriever.class.getName());

    public HttpResponse getLogs(String logServerHostname) {
        HttpGet get = new HttpGet(logServerHostname);
        try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {
            org.apache.http.HttpResponse response = httpClient.execute(get);
            String responseBody = EntityUtils.toString(response.getEntity(), "UTF-8");
            return new LogsResponse(response.getStatusLine().getStatusCode()) {
                @Override
                public void render(OutputStream outputStream) throws IOException {
                    if (response.getEntity() != null ) outputStream.write(responseBody.getBytes());
                }
            };
        } catch (IOException e) {
            log.log(Level.WARNING, "Failed to retrieve logs from log server", e);
            return new LogsResponse(404) {
                @Override
                public void render(OutputStream outputStream) throws IOException {
                    outputStream.write(e.toString().getBytes());
                }

            };
        }

    }

    private abstract static class LogsResponse extends HttpResponse {

        LogsResponse(int status) {
            super(status);
        }

        @Override
        public String getContentType() {
            return "application/json";
        }
    }
}
