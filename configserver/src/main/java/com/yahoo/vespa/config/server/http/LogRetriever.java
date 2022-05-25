// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http;

import ai.vespa.util.http.hc5.VespaHttpClientBuilder;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.yolean.Exceptions;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;

import java.io.IOException;

/**
 * @author olaaun
 */
public class LogRetriever {

    private final CloseableHttpClient httpClient = VespaHttpClientBuilder.create().build();

    public HttpResponse getLogs(String logServerUri) {
        HttpGet get = new HttpGet(logServerUri);
        try {
            return new ProxyResponse(httpClient.execute(get));
        } catch (IOException e) {
            return HttpErrorResponse.internalServerError("Failed to get logs: " + Exceptions.toMessageString(e));
        }
    }

}
