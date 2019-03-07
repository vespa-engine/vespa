// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http;

import com.yahoo.container.jdisc.HttpResponse;
import org.apache.http.Header;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.Optional;

public class LogRetriever {

    public HttpResponse getLogs(String logServerHostname) {
        HttpGet get = new HttpGet(logServerHostname);
        try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {
            return new ProxyResponse(httpClient.execute(get));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
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
