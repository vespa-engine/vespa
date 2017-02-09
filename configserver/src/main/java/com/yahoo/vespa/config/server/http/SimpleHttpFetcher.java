// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http;

import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.log.LogLevel;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.logging.Logger;

public class SimpleHttpFetcher implements HttpFetcher {
    private static final Logger logger = Logger.getLogger(SimpleHttpFetcher.class.getName());

    @Override
    public HttpResponse get(Params params, URL url) {
        try {
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setReadTimeout(params.readTimeoutMs);
            int code = connection.getResponseCode();
            String contentType = connection.getContentType();
            try (InputStream inputStream = connection.getInputStream()) {
                ProxyResponse response = new ProxyResponse(code, contentType, inputStream);
                return response;
            }
        } catch (SocketTimeoutException e) {
            String message = "Timed out after " + params.readTimeoutMs + " ms reading response from " + url;
            logger.log(LogLevel.WARNING, message, e);
            throw new RequestTimeoutException(message);
        } catch (IOException e) {
            String message = "Failed to get response from " + url;
            logger.log(LogLevel.WARNING, message, e);
            throw new InternalServerException(message);
        }
    }
}
