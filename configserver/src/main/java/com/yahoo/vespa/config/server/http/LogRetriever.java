// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http;

import ai.vespa.util.http.VespaHttpClientBuilder;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.yolean.Exceptions;
import org.apache.http.Header;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * @author olaaun
 */
public class LogRetriever {

    private final HttpClient httpClient = VespaHttpClientBuilder.create().build();
    private static final Logger logger = Logger.getLogger(LogRetriever.class.getName());

    public HttpResponse getLogs(String logServerHostname) {
        HttpGet get = new HttpGet(logServerHostname);
        try {
            return new ProxyResponse(httpClient.execute(get));
        } catch (IOException e) {
            logger.warning("Failed to get logs: " + Exceptions.toMessageString(e));
            return HttpErrorResponse.internalServerError(Exceptions.toMessageString(e));
        }
    }

    private static class ProxyResponse extends HttpResponse {

        private final org.apache.http.HttpResponse clientResponse;

        private ProxyResponse(org.apache.http.HttpResponse clientResponse) {
            super(clientResponse.getStatusLine().getStatusCode());
            this.clientResponse = clientResponse;
        }

        @Override
        public String getContentType() {
            return Optional.ofNullable(clientResponse.getFirstHeader("Content-Type"))
                    .map(Header::getValue)
                    .orElseGet(super::getContentType);
        }

        @Override
        public void render(OutputStream outputStream) throws IOException {
            clientResponse.getEntity().writeTo(outputStream);
        }
    }

}
