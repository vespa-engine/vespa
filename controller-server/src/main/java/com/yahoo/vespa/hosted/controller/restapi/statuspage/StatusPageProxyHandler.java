// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.statuspage;

import com.google.inject.Inject;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.jdisc.LoggingRequestHandler;
import com.yahoo.container.jdisc.secretstore.SecretStore;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.hosted.controller.restapi.ErrorResponse;
import com.yahoo.restapi.Path;
import com.yahoo.vespa.hosted.controller.restapi.SlimeJsonResponse;
import com.yahoo.vespa.hosted.controller.statuspage.config.StatuspageConfig;
import com.yahoo.yolean.Exceptions;

import java.net.URI;
import java.util.Optional;
import java.util.logging.Level;

/**
 * Proxies requests from the controller to StatusPage.
 *
 * @author andreer
 * @author mpolden
 */
@SuppressWarnings("unused") // Handler
public class StatusPageProxyHandler extends LoggingRequestHandler {

    private static final String secretKey = "vespa_hosted.controller.statuspage_api_key";

    private final SecretStore secretStore;
    private final URI apiUrl;

    @Inject
    public StatusPageProxyHandler(Context parentCtx, SecretStore secretStore, StatuspageConfig config) {
        super(parentCtx);
        this.secretStore = secretStore;
        this.apiUrl = URI.create(config.apiUrl());
    }

    @Override
    public HttpResponse handle(HttpRequest request) {
        try {
            switch (request.getMethod()) {
                case GET: return handleGET(request);
                default: return ErrorResponse.methodNotAllowed("Method '" + request.getMethod() + "' is not supported");
            }
        } catch (IllegalArgumentException e) {
            return ErrorResponse.badRequest(Exceptions.toMessageString(e));
        } catch (RuntimeException e) {
            log.log(Level.WARNING, "Unexpected error handling '" + request.getUri() + "'", e);
            return ErrorResponse.internalServerError(Exceptions.toMessageString(e));
        }
    }

    private HttpResponse handleGET(HttpRequest request) {
        Path path = new Path(request.getUri());
        if (!path.matches("/statuspage/v1/{page}")) {
            return ErrorResponse.notFoundError("Nothing at " + path);
        }
        StatusPageClient client = StatusPageClient.create(apiUrl, secretStore.getSecret(secretKey));
        Optional<String> since = Optional.ofNullable(request.getProperty("since"));
        Slime statusPageResponse = client.get(path.get("page"), since);
        return new SlimeJsonResponse(statusPageResponse);
    }

}
