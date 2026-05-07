// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http;

import ai.vespa.http.HttpURL;
import ai.vespa.util.http.hc5.VespaHttpClientBuilder;
import com.yahoo.container.jdisc.EmptyResponse;
import com.yahoo.container.jdisc.HttpResponse;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.util.Timeout;

import java.io.IOException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.logging.Logger;

import static java.util.logging.Level.INFO;

/**
 * @author olaaun
 */
public class LogRetriever {

    private static final Logger log = Logger.getLogger(LogRetriever.class.getName());

    private final CloseableHttpClient httpClient = VespaHttpClientBuilder.custom()
                                                                         .connectTimeout(Timeout.ofSeconds(5))
                                                                         .socketTimeout(Timeout.ofSeconds(45))
                                                                         .buildClient();

    /**
     * Fetches logs from the log server for a given application.
     * An empty response will be returned if we are unable to fetch logs and
     * the deployment is less than 5 minutes old OR we get UnknownHostException
     */
    @SuppressWarnings("deprecation")
    public HttpResponse getLogs(HttpURL logServerUri, Optional<Instant> deployTime) {
        HttpGet get = new HttpGet(logServerUri.asURI());
        try {
            return new ProxyResponse(httpClient.execute(get));
        } catch (NoRouteToHostException | UnknownHostException | SocketTimeoutException e) {
            // Application has been deleted or a real DNS issue or host not up yet, either way it's not
            // an internal server error and client should just retry
            log.log(INFO, "Communication with host " + logServerUri.asURI().getHost() + " failed when getting logs (" +
                    e.getClass().getName() + ", returning empty response");
            return new EmptyResponse();
        } catch (IOException ioe) {
            if (deployTime.isPresent() && Instant.now().isBefore(deployTime.get().plus(Duration.ofMinutes(5))))
                return new EmptyResponse();

            return new HttpErrorResponse(500, HttpErrorResponse.ErrorCode.INTERNAL_SERVER_ERROR.name(),
                                         "Failed to retrieve logs from log server (" + ioe.getClass().getName() + "): " + ioe.getMessage());
        }
    }

}
